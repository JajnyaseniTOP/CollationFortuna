package com.jj.util;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out which BAS periods a job covers, from the job's name.
 *
 * <p>The period used to be hard-coded to Q4 2026-Q3 2027 for every job. It comes
 * from the job name, which is written in one of three ways:
 *
 * <ul>
 *   <li><b>One quarter</b> — "KPF018802 - Client Name Q3 27 BAS" is Q3 2027 alone.</li>
 *   <li><b>A range</b> — "J010726 - BAS Q4 26 to Q3 27" is those four quarters.</li>
 *   <li><b>A financial year</b> — "BAS FY27" is the whole of FY2027, so all four
 *       of its quarters.</li>
 * </ul>
 *
 * <p>Quarters are named by <em>financial</em> year, the same convention the sheet's
 * Financial Quarter column uses: July to September 2026 is Q1 2027, and April to
 * June 2027 is Q4 2027. A two digit year means the financial year, so "Q3 27" is
 * Q3 of FY2027, which is January to March 2027.
 */
public final class BasPeriodParser {

	/** "Q4 26 to Q3 27", also "Q4 2026 - Q3 2027". */
	private static final Pattern RANGE = Pattern.compile(
			"(?i)Q([1-4])\\s*(\\d{2,4})\\s*(?:to|-|–|—|through)\\s*Q([1-4])\\s*(\\d{2,4})");

	/** "Q3 27", "Q3 2027". */
	private static final Pattern SINGLE = Pattern.compile("(?i)Q([1-4])\\s*(\\d{2,4})");

	/** "FY27", "FY 2027", "F/Y 27". */
	private static final Pattern FINANCIAL_YEAR = Pattern.compile("(?i)F/?Y\\s*(\\d{2,4})");

	private BasPeriodParser() {
		// static helpers only
	}

	/**
	 * The financial quarters a job covers, in order, e.g. {@code [Q4 2026, Q1 2027]}.
	 *
	 * @return an empty list when the name carries no period at all, so the caller can
	 *         say so rather than silently collating the wrong periods
	 */
	public static List<String> quartersFrom(String jobName) {
		String name = jobName == null ? "" : jobName.trim();
		List<String> quarters = new ArrayList<>();

		if (name.isEmpty()) {
			return quarters;
		}

		Matcher range = RANGE.matcher(name);
		if (range.find()) {
			int fromQuarter = Integer.parseInt(range.group(1));
			int fromYear = financialYear(range.group(2));
			int toQuarter = Integer.parseInt(range.group(3));
			int toYear = financialYear(range.group(4));
			return between(fromQuarter, fromYear, toQuarter, toYear);
		}

		// A financial year is checked before a lone quarter: "BAS FY27" has no Q in it,
		// but a name could carry both, and the explicit quarter should win.
		Matcher single = SINGLE.matcher(name);
		if (single.find()) {
			int quarter = Integer.parseInt(single.group(1));
			int year = financialYear(single.group(2));
			quarters.add("Q" + quarter + " " + year);
			return quarters;
		}

		Matcher financialYear = FINANCIAL_YEAR.matcher(name);
		if (financialYear.find()) {
			int year = financialYear(financialYear.group(1));
			return between(1, year, 4, year);
		}

		return quarters;
	}

	/**
	 * The months a job covers, in order, for a client that lodges monthly — the three
	 * months of every quarter in the period.
	 */
	public static List<YearMonth> monthsFrom(String jobName) {
		List<YearMonth> months = new ArrayList<>();
		for (String quarter : quartersFrom(jobName)) {
			months.addAll(monthsIn(quarter));
		}
		return months;
	}

	/**
	 * The three months of one financial quarter.
	 *
	 * <p>Q1 and Q2 of a financial year fall in the previous calendar year: Q1 2027 is
	 * July to September 2026.
	 */
	public static List<YearMonth> monthsIn(String financialQuarter) {
		List<YearMonth> months = new ArrayList<>();

		Matcher matcher = SINGLE.matcher(financialQuarter == null ? "" : financialQuarter.trim());
		if (!matcher.find()) {
			return months;
		}

		int quarter = Integer.parseInt(matcher.group(1));
		int year = financialYear(matcher.group(2));

		YearMonth start = firstMonthOf(quarter, year);
		for (int offset = 0; offset < 3; offset++) {
			months.add(start.plusMonths(offset));
		}
		return months;
	}

	/** The calendar month a financial quarter starts in. */
	public static YearMonth firstMonthOf(int quarter, int financialYear) {
		switch (quarter) {
			case 1:  return YearMonth.of(financialYear - 1, 7);
			case 2:  return YearMonth.of(financialYear - 1, 10);
			case 3:  return YearMonth.of(financialYear, 1);
			default: return YearMonth.of(financialYear, 4);
		}
	}

	/** Every quarter from one to another inclusive, walking financial years. */
	private static List<String> between(int fromQuarter, int fromYear, int toQuarter, int toYear) {
		List<String> quarters = new ArrayList<>();

		int quarter = fromQuarter;
		int year = fromYear;

		// Guarded rather than while(true): a malformed name should not spin forever.
		for (int step = 0; step < 40; step++) {
			quarters.add("Q" + quarter + " " + year);

			if (quarter == toQuarter && year == toYear) {
				return quarters;
			}

			quarter++;
			if (quarter > 4) {
				quarter = 1;
				year++;
			}
		}

		return quarters;
	}

	/** "27" means financial year 2027; a four digit year is taken as written. */
	private static int financialYear(String digits) {
		int value = Integer.parseInt(digits.trim());
		return value < 100 ? 2000 + value : value;
	}

	/** For logging: "Q4 2026 to Q3 2027", or "Q3 2027" for a single quarter. */
	public static String describe(List<String> quarters) {
		if (quarters.isEmpty()) {
			return "(no period found in the job name)";
		}
		if (quarters.size() == 1) {
			return quarters.get(0);
		}
		return quarters.get(0) + " to " + quarters.get(quarters.size() - 1)
				+ " (" + quarters.size() + " quarters)";
	}

	/** Lower-cased, for callers matching keywords in a job name. */
	public static String normalise(String jobName) {
		return jobName == null ? "" : jobName.toLowerCase(Locale.ENGLISH);
	}
}
