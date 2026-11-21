package tv.lunvoryx.player.epg;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class EpgIndexTest {
    private Program program(String id, String title, long start, long end) { return new Program(id, title, "", start, end); }

    @Test public void boundaryBelongsToFollowingProgram() {
        EpgIndex index = new EpgIndex(Arrays.asList(program("one", "B", 200, 300), program("one", "A", 100, 200)));
        assertEquals("A", index.now("one", 199).title);
        assertEquals("B", index.now("one", 200).title);
        assertNull(index.now("one", 300));
    }

    @Test public void nextDoesNotRepeatCurrentProgram() {
        EpgIndex index = new EpgIndex(Arrays.asList(program("one", "A", 100, 200), program("one", "B", 200, 300)));
        assertEquals("B", index.next("one", 100).title);
        assertNull(index.next("one", 200));
        assertNull(index.now("unknown", 150));
    }

    @Test public void omittedStopUsesNextActualStart() {
        EpgIndex index = new EpgIndex(Arrays.asList(program("one", "A", 100, 100), program("one", "B", 220, 300)));
        assertEquals("A", index.now("one", 210).title);
        assertEquals("B", index.now("one", 220).title);
    }

    @Test public void missingStopWithoutSuccessorDoesNotInventBroadcastDuration() {
        EpgIndex index = new EpgIndex(Collections.singletonList(program("one", "A", 100, 100)));
        assertNull(index.now("one", 101));
        assertEquals("A", index.next("one", 99).title);
    }

    @Test public void channelsNeverLeakIntoEachOther() {
        EpgIndex index = new EpgIndex(Arrays.asList(program("one", "A", 100, 200), program("two", "B", 100, 200)));
        assertEquals("A", index.now("one", 150).title);
        assertEquals("B", index.now("two", 150).title);
    }
}
