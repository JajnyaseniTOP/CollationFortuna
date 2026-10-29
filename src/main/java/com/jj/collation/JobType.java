package com.jj.collation;

import java.util.regex.Pattern;

/**
 * Which collation a job gets, decided by the job's name in XPM — the Name column of
 * the job list, e.g. "2026 Income Tax Return" or "Client Name Q3 27 BAS".
 *
 * <p>Every job is listed in the one feature file. The run reads this first, and the
 * BAS steps and the ITR steps each skip a job that is not theirs.
 */
public enum JobType {

	/** BAS; ITR is an income tax return for an individual. */
	BAS, ITR,

	/** An income tax return whose client's Business Structure could not be read: stopped, not guessed. */
	ITR_NOT_INDIVIDUAL,

	/**
	 * An income tax return for any client that is not an individual - Trust, Group,
	 * Partnership, Spouse, Company - collated with the Trust scope: the parent entity and
	 * the other entities of the job (scope 4).
	 */
	ITR_TRUST,

	/** Neither, or both — the job is left alone rather than collated the wrong way. */
	UNKNOWN;

	/**
	 * The client's XPM Business Structure decides which ITR process applies: "Individual"
	 * has its own; every other structure uses the Trust scope.
	 */
	public static boolean isIndividual(String businessStructure) {
		return businessStructure != null && businessStructure.trim().equalsIgnoreCase("Individual");
	}

	/** True when XPM gave no Business Structure: blank, "-", or the bot's own "No Value". */
	public static boolean isUnreadable(String businessStructure) {
		String value = businessStructure == null ? "" : businessStructure.trim();
		return value.isEmpty() || value.equals("-") || value.toLowerCase().startsWith("no ");
	}

	/** "Income Tax Return", or ITR as a word of its own. */
	private static final Pattern ITR_NAME = Pattern.compile("(?i)income\\s+tax\\s+return|\\bITR\\b");

	/** BAS or IAS as words of their own ("Basil" does not count), or "Activity statement". */
	private static final Pattern BAS_NAME = Pattern.compile("(?i)\\bBAS\\b|\\bIAS\\b|activity\\s+statement");

	/** The type of the job currently being run. */
	private static JobType current = UNKNOWN;

	public static JobType fromJobName(String jobName) {
		String name = jobName == null ? "" : jobName;

		boolean itr = ITR_NAME.matcher(name).find();
		boolean bas = BAS_NAME.matcher(name).find();

		if (itr == bas) {
			return UNKNOWN;
		}
		return itr ? ITR : BAS;
	}

	public static JobType current() {
		return current;
	}

	public static void setCurrent(JobType type) {
		current = type == null ? UNKNOWN : type;
	}

	/**
	 * True when the job being run is of this type. Otherwise says the step is skipped,
	 * so the run log shows why nothing happened.
	 */
	public boolean isCurrent(String step) {
		if (current == this) {
			return true;
		}
		System.out.println("  (" + step + " — skipped, this is " + describe(current) + ")");
		return false;
	}

	private static String describe(JobType type) {
		switch (type) {
			case UNKNOWN:            return "neither a BAS nor an ITR job";
			case ITR_NOT_INDIVIDUAL: return "an ITR whose client's Business Structure could not be read";
			case ITR_TRUST:          return "an ITR handled by the Trust scope";
			case ITR:                return "an individual ITR job";
			default:                 return "a BAS job";
		}
	}
}
