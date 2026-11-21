package tv.lunvoryx.player.epg;

import org.junit.Test;
import org.kxml2.io.KXmlParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;

public class XmltvParserTest {
    private final long now = XmltvTime.parse("20261116170000 +0000");

    private List<Program> parse(String source) throws IOException {
        return XmltvParser.parse(new KXmlParser(), new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)), now);
    }

    @Test public void parsesTitlesCdataEntitiesAndOffsets() throws Exception {
        List<Program> result = parse("<?xml version='1.0' encoding='UTF-8'?><tv>"
                + "<programme channel='canal-1' start='20261116173000 +0100' stop='20261116190000 +0100'>"
                + "<title lang='es'>Cine &amp; música</title><desc><![CDATA[Una película <especial>.]]></desc>"
                + "</programme></tv>");
        assertEquals(1, result.size());
        Program program = result.get(0);
        assertEquals("canal-1", program.channelId);
        assertEquals("Cine & música", program.title);
        assertEquals("Una película <especial>.", program.description);
        assertTrue(program.isOnAir(now));
    }

    @Test public void ignoresExpiredFarFutureAndInvalidPrograms() throws Exception {
        List<Program> result = parse("<tv>"
                + "<programme channel='one' start='20261110100000' stop='20261110110000'><title>Expired</title></programme>"
                + "<programme channel='one' start='20261130100000' stop='20261130110000'><title>Future</title></programme>"
                + "<programme channel='one' start='invalid'><title>Invalid</title></programme>"
                + "<programme channel='one' start='20261116180000' stop='20261116190000'><title>Upcoming</title></programme>"
                + "</tv>");
        assertEquals(1, result.size());
        assertEquals("Upcoming", result.get(0).title);
    }

    @Test public void standardExternalDoctypeDoesNotFetchDtd() throws Exception {
        List<Program> result = parse("<?xml version='1.0'?><!DOCTYPE tv SYSTEM 'http://127.0.0.1:1/never-fetch.dtd'><tv>"
                + "<programme channel='one' start='20261116180000'><title>News</title></programme></tv>");
        assertEquals(1, result.size());
    }

    @Test(expected = IOException.class) public void internalEntityDeclarationIsRejected() throws Exception {
        parse("<!DOCTYPE tv [<!ENTITY secret SYSTEM 'file:///private.txt'>]><tv>"
                + "<programme channel='one' start='20261116180000'><title>&secret;</title></programme></tv>");
    }

    @Test(expected = IOException.class) public void htmlErrorPageIsRejected() throws Exception {
        parse("<html><body>Service unavailable</body></html>");
    }

    @Test(expected = IOException.class) public void truncatedXmlDoesNotReplaceExistingGuide() throws Exception {
        parse("<tv><programme channel='one' start='20261116180000'><title>News</title>");
    }

    @Test public void optionalStopRemainsUnknownUntilIndexFindsSuccessor() throws Exception {
        List<Program> result = parse("<tv><programme channel='one' start='20261116180000'><title>News</title></programme></tv>");
        assertEquals(result.get(0).startMillis, result.get(0).endMillis);
    }
}
