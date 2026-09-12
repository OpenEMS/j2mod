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

import org.junit.Test;

import static org.junit.Assert.*;

public class SerialTransmissionWaitUtilsTest {

	private static final long MIN_EXPECTED_FUDGE_NS = 800_000L;
	private static final long MAX_EXPECTED_FUDGE_NS = 3_000_000L;

	@Test
	public void testCalcFudgedWaitTimeNs() {
		long previousFudge = 0;
		for (long i = 0; i <= 10_000_000; i += 10_000) {
			long waitTimeNs = SerialTransmissionWaitUtils.calcFudgedWaitTimeNs(i);
			assertFudgedWaitTimeInRange(i, waitTimeNs, previousFudge);
			previousFudge = waitTimeNs - i;
		}
	}

	private void assertFudgedWaitTimeInRange(long theoreticalWaitTimeNs, long fudgedWaitTimeNs, long previousFudge) {
		final long fudge = fudgedWaitTimeNs - theoreticalWaitTimeNs;

		assertTrue("Fudged has to scale with waitTime", fudge >= previousFudge);
		assertTrue("Fudged has to never undershoot", fudgedWaitTimeNs >= theoreticalWaitTimeNs);
		assertTrue("Fudge should add at least " + MIN_EXPECTED_FUDGE_NS + " ns", fudge >= MIN_EXPECTED_FUDGE_NS);
		assertTrue("Fudge should add at most " + MAX_EXPECTED_FUDGE_NS + " ns", fudge <= MAX_EXPECTED_FUDGE_NS);
	}

	@Test
	public void testCalcFudgedWaitTimeNsWithNegativeInput() {
		assertThrows(IllegalArgumentException.class, () -> SerialTransmissionWaitUtils.calcFudgedWaitTimeNs(-1));
	}

}
