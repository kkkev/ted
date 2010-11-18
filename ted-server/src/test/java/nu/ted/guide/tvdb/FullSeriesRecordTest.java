package nu.ted.guide.tvdb;

import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.Calendar;

import javax.xml.bind.DatatypeConverter;

import junit.framework.Assert;
import nu.ted.domain.TestSeriesXml;
import nu.ted.generated.TDate;
import nu.ted.generated.Episode;
import nu.ted.guide.tvdb.FullSeriesRecord.TVDBEpisode;

import org.junit.Before;
import org.junit.Test;

public class FullSeriesRecordTest
{
	private TestSeriesXml xml;

	@Before
	public void setUp()
	{
		this.xml = new TestSeriesXml();
	}

	private void assertEpisode(Episode episode, short season, short epnum, Calendar aired, String name) {
		Assert.assertEquals(season, episode.getSeason());
		Assert.assertEquals(epnum, episode.getNumber());

		aired.set(Calendar.MILLISECOND, 0);

		Assert.assertEquals(aired.getTimeInMillis(), episode.getAired().getValue());
	}

	private void zeroTimeOnCal(Calendar cal) {
		cal.set(Calendar.HOUR, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
	}

	@Test
	public void shouldReturnNextEpsiodeFromOne() throws UnsupportedEncodingException, ParseException
	{
		Calendar now = Calendar.getInstance();
		zeroTimeOnCal(now);

		Calendar then = (Calendar) now.clone();
		then.add(Calendar.DAY_OF_MONTH, 1);
		String thenPrintDate = DatatypeConverter.printDate(then);
		this.xml.addEpisode(1, 3, thenPrintDate, "Name");

		Calendar thenCompare = DatatypeConverter.parseDate(thenPrintDate);

		FullSeriesRecord list = FullSeriesRecord.create(xml.toStream());

		Episode result = list.getNextEpisode(new TDate(now.getTimeInMillis()));
		Assert.assertNotNull(result);
		assertEpisode(result, (short) 1, (short) 3, thenCompare, "Name");
	}

	@Test
	public void shouldReturnNextFromMultiple() throws UnsupportedEncodingException, ParseException
	{
		Calendar now = Calendar.getInstance();
		zeroTimeOnCal(now);

		Calendar before = ((Calendar) now.clone());
		before.add(Calendar.DAY_OF_MONTH, -1);

		Calendar after  = ((Calendar) now.clone());
		after.add(Calendar.DAY_OF_MONTH,  1);

		this.xml.addEpisode(1, 3, DatatypeConverter.printDate(before), "Name");
		String afterPrintDate = DatatypeConverter.printDate(after);
		this.xml.addEpisode(1, 4, afterPrintDate, "Name2");

		Calendar afterCompare = DatatypeConverter.parseDate(afterPrintDate);
		FullSeriesRecord list = FullSeriesRecord.create(xml.toStream());

		Episode result = list.getNextEpisode(new TDate(now.getTimeInMillis()));

		Assert.assertNotNull(result);
		assertEpisode(result, (short) 1, (short) 4, afterCompare, "Name2");

	}

	@Test
	public void getOverviewFromXml() throws UnsupportedEncodingException
	{
		FullSeriesRecord series = FullSeriesRecord.create(xml.toStream());
		Assert.assertEquals(TestSeriesXml.EXPECTED_OVERVIEW, series.getOverview());
	}

	@Test
	public void equalToAnEpisodeDependsOnlyOnSeasonEpisodeNumber()
	{
		Calendar now = Calendar.getInstance();
		TVDBEpisode episode = new TVDBEpisode(1, 3, now, "EP1");

		Episode matching = new Episode((short) 1, (short) 3,
				new TDate(now.getTimeInMillis()));
		Episode wrongSeason = new Episode((short) 2, (short) 3,
				new TDate(now.getTimeInMillis()));
		Episode wrongNumber = new Episode((short) 2, (short) 3,
				new TDate(now.getTimeInMillis()));

		Assert.assertTrue(episode.equalsEpisode(matching));
		Assert.assertFalse(episode.equalsEpisode(wrongSeason));
		Assert.assertFalse(episode.equalsEpisode(wrongNumber));
	}

	@Test
	public void testMissingFirstAired()
	{
		String ep = "  <Episode>\n";
		ep += "    <SeasonNumber>4</SeasonNumber>\n";
		ep += "    <EpisodeNumber>2</EpisodeNumber>\n";
		// No First Aired
		ep += "    <EpisodeName>Bad Ep</EpisodeName>\n";
		ep += "  </Episode>\n";
		xml.addXml(ep);

		Calendar now = Calendar.getInstance();
		zeroTimeOnCal(now);

		Calendar then = (Calendar) now.clone();
		then.add(Calendar.DAY_OF_MONTH, 1);
		String thenPrintDate = DatatypeConverter.printDate(then);

		xml.addEpisode(4, 3, thenPrintDate, "Good Ep");

		FullSeriesRecord record = FullSeriesRecord.create(xml.toStream());
		Episode e = record.getNextEpisode(new TDate(now.getTimeInMillis()));

		Assert.assertNotNull(e);
		assertEpisode(e, (short)4, (short)3, DatatypeConverter.parseDate(thenPrintDate), "Good Ep");
	}

	// TODO: decide if NextEpisode() on the date return this ep, or next

}
