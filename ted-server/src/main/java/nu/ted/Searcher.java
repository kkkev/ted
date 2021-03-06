package nu.ted;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nu.ted.domain.EpisodeBackendWrapper;
import nu.ted.domain.SeriesBackendWrapper;
import nu.ted.domain.TedBackendWrapper;
import nu.ted.generated.Episode;
import nu.ted.generated.EpisodeStatus;
import nu.ted.generated.Series;
import nu.ted.generated.Ted;
import nu.ted.generated.TorrentSource;
import nu.ted.torrent.TorrentRef;
import nu.ted.torrent.TorrentTitleMatcher;
import nu.ted.torrent.search.TorrentSourceIndex;
import nu.ted.torrent.search.TorrentSourceType;
import nu.ted.www.DirectPageLoader;
import nu.ted.www.PageLoader;

class Searcher implements Runnable {

	private Logger logger = LoggerFactory.getLogger(Searcher.class);

	private static boolean scheduled = false;
	private static Object scheduledLock = new Object();
	private static ScheduledFuture<?> future = null;
	private static ScheduledExecutorService executor;
	private static Ted ted;

	public void run() {
		logger.debug("Running Searcher");
		scheduled = false;

		try {

			if (executor == null) {
				throw new RuntimeException("Unable to schedule Searcher without it being setup.");
			}

			if (new TedBackendWrapper(ted).hasMissingEpisodes()) {
				searchForMissingEpisodes();
			}

			if (new TedBackendWrapper(ted).hasMissingEpisodes()) {
				// Reschedule for an hour later
				// TODO: config?
				scheduleRun(1, TimeUnit.HOURS);
			}
		} catch (Throwable ex) {
			LoggerFactory.getLogger(Server.class).warn("TorrentSearcher crashed", ex);
		}
	}

	public void searchForMissingEpisodes() {

		List<Series> missingSeries = new TedBackendWrapper(ted).getSeriesWithMissingEpisodes();

		for (Series series : missingSeries) {
			List<Episode> missingEpisodes = new SeriesBackendWrapper(series).getMissingEpisodes();
			for (Episode episode : missingEpisodes) {

				List<TorrentTitleMatcher> matchers = new LinkedList<TorrentTitleMatcher>();
				matchers.add(new SeriesBackendWrapper(series));
				matchers.add(new EpisodeBackendWrapper(episode, ted.getConfig().getEpisodeKeywords()));

				List<TorrentSource> sources = ted.getConfig().getTorrentSources();
				List<TorrentRef> torrents = new LinkedList<TorrentRef>();
				for (TorrentSource source : sources) {
					TorrentSourceType torrentSourceType =
						TorrentSourceIndex.getTorrentSourceType(source);

					try {
						torrents.addAll(torrentSourceType.search(matchers));
					} catch (DataRetrievalException e) {
						logger.warn("Unable to retrieve torrents from {}", source.getLocation(), e);
					}
				}

				if (torrents.isEmpty()) {
					continue;
				}

				// Should have torrents now

				// TODO: find best torrent
				TorrentRef bestTorrent = torrents.get(0);

				String filename = series.getName() + " S" + episode.getSeason() + "E" + episode.getNumber();
				try {
					PageLoader pageLoader = new DirectPageLoader();
					InputStream inputStream = pageLoader.openStream(bestTorrent.getLink());

					File output = getNewFilename(filename);

					OutputStream outputStream;
					outputStream = new FileOutputStream(output);
					byte buf[] = new byte[1024];
					int len;
					while((len = inputStream.read(buf)) > 0) {
						outputStream.write(buf, 0, len);
					}
					outputStream.close();
					inputStream.close();
					episode.setStatus(EpisodeStatus.FOUND);
					// TODO: Send event
				} catch (FileNotFoundException e) {
					logger.warn("Unable to open file for write: {}", filename, e);
				} catch (IOException e) {
					logger.warn("Error downloading file {}", filename, e);
				} catch (DataTransferException e) {
					logger.warn("Error downloading file {}", filename, e);
				}
			}
		}
	}

	private File getNewFilename(String filename) {
		File newFile = new File(filename);
		if (newFile.exists()) {
			int i = 2;
			while (true) {
				String backup_filename = filename + "-" + i;
				newFile = new File(backup_filename);
				if (!newFile.exists()) {
					break;
				}
			}
		}
		return newFile;
	}

	static void setup(ScheduledExecutorService executor, Ted ted) {
		Searcher.executor = executor;
		Searcher.ted = ted;
	}

	public static void scheduleRun(long delay, TimeUnit timeUnit) {
		if (executor == null) {
			throw new RuntimeException("Unable to schedule Searcher without it being setup.");
		}
		synchronized (scheduledLock) {
			if (scheduled == false) {
				future = executor.schedule(new Searcher(), delay, timeUnit);
				scheduled = true;
			} else {
				long origDelay = future.getDelay(timeUnit);

				// Use the sooner of the two delays for the next time.
				if (delay < origDelay) {
					if (future.cancel(false))
						future = executor.schedule(new Searcher(), delay, timeUnit);
				}
			}
		}
	}

	public static void scheduleRun() {
		if (executor == null) {
			throw new RuntimeException("Unable to schedule Searcher without it being setup.");
		}
		scheduleRun(0, TimeUnit.SECONDS);
	}
}