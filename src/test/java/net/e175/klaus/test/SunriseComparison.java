package net.e175.klaus.test;

import static net.e175.klaus.solarpositioning.SunriseResult.*;

import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;
import com.luckycatlabs.sunrisesunset.dto.Location;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjuster;
import java.util.Calendar;
import java.util.GregorianCalendar;
import net.e175.klaus.solarpositioning.DeltaT;
import net.e175.klaus.solarpositioning.SPA;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.shredzone.commons.suncalc.SunTimes;

class SunriseComparisonTest {

  /**
   * Adjusts given Temporal object to the <em>nearest</em> minute (rounding up from 30 seconds),
   * assuming it's got the necessary field (SECOND_OF_MINUTE).
   */
  static final TemporalAdjuster NEAREST_MINUTE =
      (t) -> {
        if (t.get(ChronoField.SECOND_OF_MINUTE) >= 30) {
          t = t.plus(1, ChronoUnit.MINUTES);
        }
        return t.with(ChronoField.SECOND_OF_MINUTE, 0).with(ChronoField.NANO_OF_SECOND, 0);
      };

  private static ZonedDateTime calToZdt(Calendar cal) {
    return cal != null ? ZonedDateTime.ofInstant(cal.toInstant(), ZoneOffset.UTC) : null;
  }

  @BeforeAll
  static void beforeAllTests() {
    System.out.println("date location algo type sunrise sunset");
  }

  @ParameterizedTest
  @CsvFileSource(resources = "/sunrise/usno_reference_testdata.csv")
  void testBulkUSNOReferenceValues(
      ZonedDateTime dateTime,
      String location,
      double lat,
      double lon,
      String type,
      LocalTime sunrise,
      LocalTime sunset) {

    System.out.printf(
        "%s %s %s %s %s %s%n", dateTime.toLocalDate(), location, "USNO", type, sunrise, sunset);

    // SPA via solarpositioning
    System.out.printf("%s %s %s ", dateTime.toLocalDate(), location, "SPA");
    var spaResult =
        SPA.calculateSunriseTransitSet(dateTime, lat, lon, DeltaT.estimate(dateTime.toLocalDate()));
    switch (spaResult) {
      case RegularDay regular ->
          System.out.printf(
              "NORMAL %s %s%n",
              regular.sunrise().toLocalTime().with(NEAREST_MINUTE),
              regular.sunset().toLocalTime().with(NEAREST_MINUTE));
      case AllDay day -> System.out.printf("ALL_DAY null null%n");
      case AllNight night -> System.out.printf("ALL_NIGHT null null%n");
    }

    // sunrisesunsetlib
    System.out.printf("%s %s %s ", dateTime.toLocalDate(), location, "SRSL");
    Location loc = new Location(lat, lon);
    SunriseSunsetCalculator calculator = new SunriseSunsetCalculator(loc, "UTC");
    var srslSunrise =
        calToZdt(calculator.getOfficialSunriseCalendarForDate(GregorianCalendar.from(dateTime)));
    var srslSunset =
        calToZdt(calculator.getOfficialSunsetCalendarForDate(GregorianCalendar.from(dateTime)));
    if (srslSunrise != null && srslSunset != null) {
      System.out.printf("NORMAL %s %s%n", srslSunrise.toLocalTime(), srslSunset.toLocalTime());
    } else {
      System.out.printf("UNKNOWN null null%n"); // SRSL can't tell if it's polar day or night?
    }

    // commons suncalc
    System.out.printf("%s %s %s ", dateTime.toLocalDate(), location, "commons");
    SunTimes times =
        SunTimes.compute()
            .on(dateTime)
            .oneDay() // limit calculation to this day (important!)
            .at(lat, lon)
            .execute();
    if (times.isAlwaysDown()) {
      System.out.printf("ALL_NIGHT null null%n");
    } else if (times.isAlwaysUp()) {
      System.out.printf("ALL_DAY null null%n");
    } else {
      System.out.printf(
          "NORMAL %s %s%n",
          times.getRise().toLocalTime().with(NEAREST_MINUTE),
          times.getSet().toLocalTime().with(NEAREST_MINUTE));
    }
  }
}
