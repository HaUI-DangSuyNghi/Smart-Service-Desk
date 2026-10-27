package vn.haui.dx.apps.servicedesk;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static vn.haui.dx.apps.servicedesk.AssignmentSuggestionController.*;

class AssignmentSuggestionTest {
    private Candidate agent(String subject, long load, long assigned) {
        return new Candidate(subject,subject,true,load,assigned,null,false,null);
    }
    @Test void noEligibleAgentOrOneAgent() {
        assertTrue(rank(List.of(),null).isEmpty());
        assertEquals("a",rank(List.of(agent("a",90,3)),null).get(0).subject());
        assertTrue(rank(List.of(new Candidate("a","a",false,0,0,null,false,"OFF_DUTY")),null).isEmpty());
        assertTrue(rank(List.of(new Candidate("a","a",true,0,0,null,false,"IS_REQUESTER")),null).isEmpty());
    }
    @Test void loadWinsOutsideStrictFifteenMinuteBand() {
        assertEquals("a",rank(List.of(agent("b",30,0),agent("a",15,9)),null).get(0).subject());
    }
    @Test void fewerDailyAssignmentsWinsInsideBand() {
        assertEquals("b",rank(List.of(agent("a",15,9),agent("b",29,0)),null).get(0).subject());
    }
    @Test void minimumAnchoredBandsAvoidNonTransitivePairwiseComparison() {
        var input=List.of(agent("a",0,3),agent("b",14,2),agent("c",28,0));
        assertEquals(List.of("b","a","c"),rank(input,null).stream().map(Candidate::subject).toList());
        assertEquals(rank(input,null),rank(List.of(input.get(2),input.get(0),input.get(1)),null));
    }
    @Test void cursorWrapsAndRemainsDeterministicWhenPreviousAgentLeaves() {
        var input=List.of(agent("a",0,0),agent("c",0,0),agent("e",0,0));
        assertEquals("a",rank(input,null).get(0).subject());
        assertEquals("c",rank(input,"a").get(0).subject());
        assertEquals("e",rank(input,"d").get(0).subject());
        assertEquals("a",rank(input,"e").get(0).subject());
        assertEquals(rank(input,"a"),rank(input,"a"));
    }
}
