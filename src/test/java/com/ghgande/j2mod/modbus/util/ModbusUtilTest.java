package com.ghgande.j2mod.modbus.util;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class ModbusUtilTest {

	@Test
	public void toHexEncodesEmptyArray() {
		assertEquals("", ModbusUtil.toHex(new byte[0]));
	}

	@Test
	public void toHexEncodesBytesAsUppercaseSpaceSeparatedHex() {
		byte[] data = {
				0x00,
				0x0F,
				0x10,
				0x7F,
				(byte) 0x80,
				(byte) 0xFF
		};

		assertEquals("00 0F 10 7F 80 FF", ModbusUtil.toHex(data));
	}

	@Test
	public void toHexUsesUnsignedByteValues() {
		assertEquals("80 FF", ModbusUtil.toHex(new byte[] {
				(byte) 0x80,
				(byte) 0xFF
		}));
	}

	@Test
	public void toHexUsesEndExclusiveRange() {
		byte[] data = { 0x00, 0x11, 0x22, 0x33, 0x44 };

		assertEquals("11 22 33", ModbusUtil.toHex(data, 1, 4));
	}

	@Test
	public void toHexSupportsSingleByteRange() {
		byte[] data = { 0x12, (byte) 0xAB, 0x34 };

		assertEquals("AB", ModbusUtil.toHex(data, 1, 2));
	}

	@Test
	public void toHexReturnsEmptyStringForEmptyRange() {
		byte[] data = { 0x12, 0x34 };

		assertEquals("", ModbusUtil.toHex(data, 1, 1));
	}

	@Test
	public void toHexClampsEndToArrayLength() {
		byte[] data = { 0x12, 0x34, 0x56 };

		assertEquals("34 56", ModbusUtil.toHex(data, 1, 99));
	}

	@Test
	public void toHexIntEncodesLowerByteAsAscii() {
		assertArrayEquals(
				"00".getBytes(StandardCharsets.US_ASCII),
				ModbusUtil.toHex(0x00));

		assertArrayEquals(
				"0F".getBytes(StandardCharsets.US_ASCII),
				ModbusUtil.toHex(0x0F));

		assertArrayEquals(
				"10".getBytes(StandardCharsets.US_ASCII),
				ModbusUtil.toHex(0x10));

		assertArrayEquals(
				"FF".getBytes(StandardCharsets.US_ASCII),
				ModbusUtil.toHex(0xFF));
	}

	@Test
	public void toHexIntUsesOnlyTheLowByte() {
		assertArrayEquals(
				"AB".getBytes(StandardCharsets.US_ASCII),
				ModbusUtil.toHex(0x12AB));

		assertArrayEquals(
				"FF".getBytes(StandardCharsets.US_ASCII),
				ModbusUtil.toHex(-1));
	}
}
