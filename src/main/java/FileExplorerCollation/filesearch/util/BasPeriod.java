package FileExplorerCollation.filesearch.util;

/**
 * One month of a BAS period, read off a ClientData.xls row.
 *
 * Two different years matter and they are not always the same one:
 *
 * <ul>
 *   <li><b>calendar year</b> - from column N ("Apr 2026"), used in the AS file
 *       name, e.g. "2026_April_AS_ClientName".</li>
 *   <li><b>financial year</b> - from column R ("Q1 2027"), used to pick the
 *       year folder on the server. July onwards falls into the next financial
 *       year, so "Jul 2026" lives under the 2027 folder.</li>
 * </ul>
 */
public final class BasPeriod {

    private static final String[] MONTH_NAMES = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };

    private final String monthName;
    private final String calendarYear;
    private final String financialYear;

    /** "Q4" for a quarterly client, null for a monthly one. */
    private final String quarterName;

    private BasPeriod(String monthName, String calendarYear, String financialYear, String quarterName) {
        this.monthName = monthName;
        this.calendarYear = calendarYear;
        this.financialYear = financialYear;
        this.quarterName = quarterName;
    }

    /** True when this row covers a quarter rather than a single month. */
    public boolean isQuarterly() {
        return quarterName != null;
    }

    /** Quarter folder name, e.g. "Q4". Null for monthly clients. */
    public String getQuarterName() {
        return quarterName;
    }

    /** Full month name, e.g. "April". */
    public String getMonthName() {
        return monthName;
    }

    /** First three letters, e.g. "Apr" - folder names on the server are sometimes misspelled. */
    public String getMonthAbbreviation() {
        return monthName.substring(0, 3);
    }

    /** Calendar year the month falls in, e.g. "2026". Used in the AS file name. */
    public String getCalendarYear() {
        return calendarYear;
    }

    /** Financial year folder the month lives under, e.g. "2027" for July 2026. */
    public String getFinancialYear() {
        return financialYear;
    }

    /** Last day of the period, e.g. 30 April 2026 for a monthly April row. */
    public java.time.LocalDate getPeriodEndDate() {

        if (!calendarYear.matches("\\d{4}")) {
            return null;
        }

        int monthNumber = indexOfMonth(monthName) + 1;

        return java.time.YearMonth.of(Integer.parseInt(calendarYear), monthNumber).atEndOfMonth();
    }

    /**
     * How the cover letter describes the period, e.g.
     * "month ended 30 April 2026" or "quarter ended 31 March 2026".
     */
    public String getPeriodDescription() {

        java.time.LocalDate endDate = getPeriodEndDate();

        String label = isQuarterly() ? "quarter" : "month";

        if (endDate == null) {
            return label + " ended " + monthName + " " + calendarYear;
        }

        return label + " ended " + endDate.format(
                java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy"));
    }

    @Override
    public String toString() {

        String period = isQuarterly()
                ? quarterName + " (" + monthName + " " + calendarYear + ")"
                : monthName + " " + calendarYear;

        return period + " [year folder " + financialYear + "]";
    }

    /**
     * Builds a period from column N ("Apr 2026") and column R ("Q1 2027").
     *
     * When the financial quarter is missing the financial year is worked out
     * from the month instead - July or later rolls into the next year.
     *
     * @return the period, or null if the month could not be read
     */
    public static BasPeriod parse(String monthValue, String financialQuarterValue) {

        if (monthValue == null || monthValue.trim().isEmpty()) {
            return null;
        }

        // A quarterly client's row says "Q4" or "Q4 2026" rather than a month
        if (monthValue.trim().matches("(?i)Q[1-4]\\b.*")) {
            return parseQuarter(monthValue.trim(), financialQuarterValue);
        }

        String[] monthParts = monthValue.trim().split("\\s+");

        String monthName = toFullMonthName(monthParts[0]);

        if (monthName == null) {
            return null;
        }

        String calendarYear = monthParts.length > 1 ? monthParts[1] : "";

        String financialYear = yearFromQuarter(financialQuarterValue);

        if (financialYear.isEmpty()) {
            financialYear = deriveFinancialYear(monthName, calendarYear);
        }

        return new BasPeriod(monthName, calendarYear, financialYear, null);
    }

    /**
     * Builds a quarterly period.
     *
     * The AS document inside a quarter folder is named for the <em>last</em>
     * month of that quarter, and the first two quarters of a financial year
     * fall in the previous calendar year:
     *
     * <pre>
     *   FY2026 Q1 -> 2025_September    Q3 -> 2026_March
     *   FY2026 Q2 -> 2025_December     Q4 -> 2026_June
     * </pre>
     */
    private static BasPeriod parseQuarter(String quarterValue, String financialQuarterValue) {

        String quarterName = quarterValue.substring(0, 2).toUpperCase();

        int quarterNumber = Integer.parseInt(quarterName.substring(1));

        String financialYear = yearFromQuarter(financialQuarterValue);

        if (financialYear.isEmpty()) {
            financialYear = yearFromQuarter(quarterValue);
        }

        String monthName;
        int yearOffset;

        switch (quarterNumber) {
            case 1:  monthName = "September"; yearOffset = -1; break;
            case 2:  monthName = "December";  yearOffset = -1; break;
            case 3:  monthName = "March";     yearOffset = 0;  break;
            default: monthName = "June";      yearOffset = 0;  break;
        }

        String calendarYear = financialYear.matches("\\d{4}")
                ? String.valueOf(Integer.parseInt(financialYear) + yearOffset)
                : financialYear;

        return new BasPeriod(monthName, calendarYear, financialYear, quarterName);
    }

    /** "Apr" or "April" -> "April"; null when it is not a month at all. */
    private static String toFullMonthName(String value) {

        String candidate = value.trim().toLowerCase();

        if (candidate.length() < 3) {
            return null;
        }

        String prefix = candidate.substring(0, 3);

        for (String monthName : MONTH_NAMES) {
            if (monthName.toLowerCase().startsWith(prefix)) {
                return monthName;
            }
        }

        return null;
    }

    /** "Q1 2027" -> "2027". */
    private static String yearFromQuarter(String financialQuarterValue) {

        if (financialQuarterValue == null) {
            return "";
        }

        String[] parts = financialQuarterValue.trim().split("\\s+");

        String lastPart = parts[parts.length - 1];

        return lastPart.matches("\\d{4}") ? lastPart : "";
    }

    /** July onwards belongs to the next financial year. */
    private static String deriveFinancialYear(String monthName, String calendarYear) {

        if (!calendarYear.matches("\\d{4}")) {
            return calendarYear;
        }

        int monthNumber = indexOfMonth(monthName) + 1;
        int year = Integer.parseInt(calendarYear);

        return String.valueOf(monthNumber >= 7 ? year + 1 : year);
    }

    private static int indexOfMonth(String monthName) {

        for (int i = 0; i < MONTH_NAMES.length; i++) {
            if (MONTH_NAMES[i].equals(monthName)) {
                return i;
            }
        }

        return 0;
    }
}
