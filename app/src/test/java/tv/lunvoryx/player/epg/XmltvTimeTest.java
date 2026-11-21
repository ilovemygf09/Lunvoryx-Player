package tv.lunvoryx.player.epg;

import org.junit.Test;

import java.util.TimeZone;

import static org.junit.Assert.*;

public class XmltvTimeTest {
    @Test public void offsetsResolveToSameInstant() {
        assertEquals(XmltvTime.parse("20261116180000 +0100"), XmltvTime.parse("20261116170000 +0000"));
        assertEquals(XmltvTime.parse("20261116170000 +0000"), XmltvTime.parse("20261116123000 -0430"));
    }

    @Test public void omittedZoneIsUtcRegardlessOfDeviceZone() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            assertEquals(XmltvTime.parse("20261116170000 +0000"), XmltvTime.parse("20261116170000"));
        } finally { TimeZone.setDefault(previous); }
    }

    @Test public void invalidDatesAndOffsetsAreRejected() {
        assertEquals(-1, XmltvTime.parse("20260230180000 +0000"));
        assertEquals(-1, XmltvTime.parse("20261116180000 +2560"));
        assertEquals(-1, XmltvTime.parse("20261116180000 +0160"));
        assertEquals(-1, XmltvTime.parse("20261116180000 trailing"));
        assertEquals(-1, XmltvTime.parse(null));
    }

    @Test public void minutePrecisionAndZuluAreSupported() {
        assertEquals(XmltvTime.parse("20261116170000 +0000"), XmltvTime.parse("202611161700 Z"));
    }

    @Test public void dstOffsetsAreTakenFromEachProgram() {
        assertEquals(3600000, XmltvTime.parse("20261025023000 +0100") - XmltvTime.parse("20261025023000 +0200"));
    }
}
