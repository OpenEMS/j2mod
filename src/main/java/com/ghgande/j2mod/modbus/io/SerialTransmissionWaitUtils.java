/*
 * Copyright 2002-2016 jamod & j2mod development teams
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ghgande.j2mod.modbus.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Utility class for waiting for serial transmission to complete.
 */
class SerialTransmissionWaitUtils {

	private static final Logger logger = LoggerFactory.getLogger(SerialTransmissionWaitUtils.class);

	private static final String PROP_EXTRA_US = "j2mod.serial.tx.fudge.extra.us";
	private static final String ENV_EXTRA_US = "J2MOD_SERIAL_TX_FUDGE_EXTRA_US";

	/**
	 * The number of nanoseconds in a millisecond
	 */
	private static final double NS_IN_A_MS = 1_000_000.0;
	/**
	 * Minimum sleep duration in nanoseconds.
	 * Below this, only busy-waiting is accurate.
	 */
	private static final long SLEEP_MIN_NS = 3_000_000L;

	/**
	 * Safety buffer subtracted from sleep time
	 * so the thread wakes up early and finishes precision timing via LockSupport.parkNanos() or busy-waiting.
	 */
	private static final long SLEEP_MARGIN_NS = 800_000L;

	/**
	 * Threshold for using LockSupport.parkNanos() instead of busy-waiting.
	 * Below this, busy-waiting is more accurate.
	 */
	private static final long PARK_THRESHOLD_NS = 50_000L;

	private static final long LONG_SHORT_WAIT_THRESHOLD_NS = 5_000_000L;

	private static final double WAIT_FOR_TRANSMISSION_FUDGE_FACTOR = 0.22;
	private static final double WAIT_FOR_TRANSMISSION_FUDGE_EXPONENT = 0.96;
	private static final long WAIT_FOR_TRANSMISSION_FUDGE_MARGIN_NS;

	public static final long WAIT_FOR_TRANSMISSION_MIN_FUDGE_NS;
	public static final long WAIT_FOR_TRANSMISSION_MAX_FUDGE_NS;

	/**
	 * Calculates a realistic wait time threshold (in nanoseconds) by adding an empirical
	 * overhead ("fudge factor") to the theoretical transmission duration.
	 * <p>
	 * Standard baud-rate calculations account mainly for wire time and can miss OS scheduling,
	 * USB/driver latency, and adapter switching delays. This non-linear power-law model was
	 * empirically tuned from logic-analyzer measurements on constrained Linux hardware
	 * (BeagleBone Black, USB-to-RS485) across many runs and baud rates.
	 *
	 * @param theoreticalTransmissionTimeNs The baseline calculated transmission time in nanoseconds.
	 * @return recommended total wait time in nanoseconds; conservative for tested edge-device conditions
	 * @throws IllegalArgumentException if the theoretical transmission time is negative
	 */
	static long calcFudgedWaitTimeNs(double theoreticalTransmissionTimeNs) {
		if (theoreticalTransmissionTimeNs < 0) {
			throw new IllegalArgumentException("Theoretical transmission time must be non-negative.");
		}
		final double fudgeCalc = WAIT_FOR_TRANSMISSION_FUDGE_MARGIN_NS //
				+ WAIT_FOR_TRANSMISSION_FUDGE_FACTOR //
				* Math.pow(theoreticalTransmissionTimeNs, WAIT_FOR_TRANSMISSION_FUDGE_EXPONENT);
		final double fudgeValue = Math.max(WAIT_FOR_TRANSMISSION_MIN_FUDGE_NS, Math.min(fudgeCalc, WAIT_FOR_TRANSMISSION_MAX_FUDGE_NS));
		return Math.round(theoreticalTransmissionTimeNs + fudgeValue);
	}

	static {
		final long EXTRA_OFFSET_US = resolveExtraOffsetUs();
		long extraOffsetNs = TimeUnit.MICROSECONDS.toNanos(EXTRA_OFFSET_US);

		WAIT_FOR_TRANSMISSION_FUDGE_MARGIN_NS = 700_000L + extraOffsetNs;
		WAIT_FOR_TRANSMISSION_MIN_FUDGE_NS = 800_000L + extraOffsetNs;
		WAIT_FOR_TRANSMISSION_MAX_FUDGE_NS = 3_000_000L + extraOffsetNs;
	}

	/**
	 * Resolves the extra offset.
	 */
	private static long resolveExtraOffsetUs() {
		String rawValue = System.getProperty(PROP_EXTRA_US);
		String source = "JVM property (" + PROP_EXTRA_US + ")";

		if (rawValue == null || rawValue.trim().isEmpty()) {
			rawValue = System.getenv(ENV_EXTRA_US);
			source = "Env Var (" + ENV_EXTRA_US + ")";
		}

		if (rawValue == null || rawValue.trim().isEmpty()) {
			return 0L;
		}

		try {
			return Long.parseLong(rawValue.trim());
		} catch (NumberFormatException e) {
			logger.warn("Failed to parse long from {} value '{}'. Defaulting to 0 us.", source, rawValue);
			return 0L;
		}
	}

	/**
	 * Waits for the calculated transmission time, using a combination of sleep, park, and busy-waiting.
	 *
	 * @param transmissionTimeNanos The theoretical transmission time in nanoseconds.
	 */
	@SuppressWarnings("StatementWithEmptyBody")
	public static void waitForTransmission(double transmissionTimeNanos) {
		if (transmissionTimeNanos <= 0) {
			return;
		}

		final long fudgedWaitTimeNs = calcFudgedWaitTimeNs(transmissionTimeNanos);
		final long targetEndNanos = System.nanoTime() + fudgedWaitTimeNs;

		try {
			long remainingNanos = targetEndNanos - System.nanoTime();
			if (remainingNanos >= (SLEEP_MIN_NS + SLEEP_MARGIN_NS)) {
				final long sleepMargin = fudgedWaitTimeNs > LONG_SHORT_WAIT_THRESHOLD_NS ? 0 : SLEEP_MARGIN_NS;
				long sleepMillis = (long) ((remainingNanos - sleepMargin) / NS_IN_A_MS);
				Thread.sleep(sleepMillis);
			}
			remainingNanos = targetEndNanos - System.nanoTime();
			if (remainingNanos > PARK_THRESHOLD_NS * 2) {
				LockSupport.parkNanos(remainingNanos - PARK_THRESHOLD_NS);
			}
			while (System.nanoTime() < targetEndNanos) {
				// Pure busy wait, for precision.
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			logger.debug("waitForTransmission interrupted.", e);
		} catch (RuntimeException ex) {
			logger.debug("waitForTransmission failed with exception.", ex);
		}
	}

	private SerialTransmissionWaitUtils() {
		// Private constructor to prevent instantiation
	}

}
