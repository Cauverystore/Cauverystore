package com.cauverystore.service;

import com.cauverystore.entities.ReturnRequest;
import com.cauverystore.repository.ReturnRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * When a return is actually processed.
 *
 * A credit note reduces the seller's output tax liability for the month it is issued in, and it
 * is what the refund rests on. It used to be issued on APPROVED - the moment somebody agreed the
 * customer <em>may</em> send the goods back, while the goods were still with the customer. A
 * parcel that never arrived, or arrived as the wrong item, or arrived broken and was refused,
 * had already had its tax credited and its money returned, and nothing put that back.
 */
@ExtendWith(MockitoExtension.class)
// The save stub goes unused in the tests that refuse a transition - which is the point of them.
@MockitoSettings(strictness = Strictness.LENIENT)
class ReturnLifecycleTest {

    @Mock private ReturnRequestRepository returnRepo;
    @Mock private CreditNoteService creditNoteService;

    private ReturnRequestService service;
    private ReturnRequest rr;

    @BeforeEach
    void setUp() {
        service = new ReturnRequestService(returnRepo, creditNoteService);
        rr = new ReturnRequest();
        rr.setStatus(ReturnRequestService.REQUESTED);
        when(returnRepo.findById(1L)).thenReturn(Optional.of(rr));
        when(returnRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void approvingAReturnDoesNotCreditAnything() {
        // The defect this exists for. Approval means "yes, send it back" - the goods are still
        // with the customer, and there is nothing yet to credit.
        service.updateStatus(1L, ReturnRequestService.APPROVED);

        verifyNoInteractions(creditNoteService);
        assertEquals(ReturnRequestService.APPROVED, rr.getStatus());
    }

    @Test
    void receivingTheGoodsStillDoesNotCreditThemUntilTheyAreChecked() {
        // Received is not accepted. The parcel is here; whether it holds what it should is a
        // separate question, and refusing it after crediting would be too late.
        rr.setStatus(ReturnRequestService.APPROVED);

        service.updateStatus(1L, ReturnRequestService.RECEIVED);

        verifyNoInteractions(creditNoteService);
        assertEquals("PENDING", rr.getQualityCheckStatus());
    }

    @Test
    void completingTheReturnIsWhatIssuesTheCreditNote() {
        rr.setStatus(ReturnRequestService.RECEIVED);

        service.updateStatus(1L, ReturnRequestService.COMPLETED);

        verify(creditNoteService).generateReturnCreditNote(eq(1L), any());
        assertEquals("PASSED", rr.getQualityCheckStatus());
    }

    @Test
    void aReturnCannotBeCompletedBeforeTheGoodsAreBack() {
        // Without this the rule above is only a convention. Jumping straight here would credit
        // the tax and refund the money on a parcel nobody has seen.
        ReturnRequestService.InvalidReturnTransitionException e = assertThrows(
                ReturnRequestService.InvalidReturnTransitionException.class,
                () -> service.updateStatus(1L, ReturnRequestService.COMPLETED));

        assertTrue(e.getMessage().contains("have not been received"), e.getMessage());
        verifyNoInteractions(creditNoteService);
        assertEquals(ReturnRequestService.REQUESTED, rr.getStatus());
    }

    @Test
    void anApprovedReturnStillCannotSkipStraightToCompleted() {
        rr.setStatus(ReturnRequestService.APPROVED);

        assertThrows(ReturnRequestService.InvalidReturnTransitionException.class,
                () -> service.updateStatus(1L, ReturnRequestService.COMPLETED));
        verifyNoInteractions(creditNoteService);
    }

    @Test
    void goodsRefusedAtTheCheckAreRejectedAndNeverCredited() {
        rr.setStatus(ReturnRequestService.RECEIVED);

        service.updateStatus(1L, ReturnRequestService.REJECTED);

        assertEquals("FAILED", rr.getQualityCheckStatus());
        verifyNoInteractions(creditNoteService);
    }

    @Test
    void refundingCannotHappenWithoutCompletingFirst() {
        rr.setStatus(ReturnRequestService.APPROVED);

        ReturnRequestService.InvalidReturnTransitionException e = assertThrows(
                ReturnRequestService.InvalidReturnTransitionException.class,
                () -> service.updateStatus(1L, ReturnRequestService.REFUNDED));

        assertTrue(e.getMessage().toLowerCase().contains("refund"), e.getMessage());
    }

    @Test
    void movingThroughCompletedAndThenRefundedDoesNotCreditTwice() {
        // The repository guard in CreditNoteService returns the existing note rather than making
        // a second one, but the call happening twice is worth stating: a duplicate credit note
        // would reduce the month's liability twice over.
        rr.setStatus(ReturnRequestService.RECEIVED);
        service.updateStatus(1L, ReturnRequestService.COMPLETED);
        service.updateStatus(1L, ReturnRequestService.REFUNDED);

        verify(creditNoteService, times(2)).generateReturnCreditNote(eq(1L), any());
        // Both calls hit the same return, and the second finds the note already there.
    }

    @Test
    void aFinalStateCannotBeReopened() {
        rr.setStatus(ReturnRequestService.REJECTED);

        assertThrows(ReturnRequestService.InvalidReturnTransitionException.class,
                () -> service.updateStatus(1L, ReturnRequestService.COMPLETED));
    }

    @Test
    void restatingTheCurrentStatusIsNotTreatedAsAMove() {
        // Screens re-post the status they are showing. That must not be read as a transition and
        // rejected, but it must not re-issue anything either.
        rr.setStatus(ReturnRequestService.APPROVED);

        assertDoesNotThrow(() -> service.updateStatus(1L, ReturnRequestService.APPROVED));
        verifyNoInteractions(creditNoteService);
    }

    @Test
    void aCreditNoteFailureDoesNotLoseTheStatusChange() {
        // The goods are back either way. Losing that because a document could not be produced
        // would leave the warehouse and the system disagreeing about where the parcel is.
        rr.setStatus(ReturnRequestService.RECEIVED);
        doThrow(new RuntimeException("invoice missing"))
                .when(creditNoteService).generateReturnCreditNote(any(), any());

        assertDoesNotThrow(() -> service.updateStatus(1L, ReturnRequestService.COMPLETED));
        assertEquals(ReturnRequestService.COMPLETED, rr.getStatus());
    }

    @Test
    void returnsStoredAsPendingAreStillMovable() {
        // Every return raised through My Orders was written as PENDING. Introducing the lifecycle
        // without recognising that name stranded all of them: the only answer to any move was
        // "unrecognised state", so no return in the system could be progressed at all.
        rr.setStatus("PENDING");

        assertDoesNotThrow(() -> service.updateStatus(1L, ReturnRequestService.APPROVED));
        assertEquals(ReturnRequestService.APPROVED, rr.getStatus());
    }

    @Test
    void aPendingReturnStillCannotJumpStraightToCompleted() {
        // The alias must not become a way round the rule it was added alongside.
        rr.setStatus("PENDING");

        assertThrows(ReturnRequestService.InvalidReturnTransitionException.class,
                () -> service.updateStatus(1L, ReturnRequestService.COMPLETED));
        verifyNoInteractions(creditNoteService);
    }

    @Test
    void olderWordingForTheSameStageIsUnderstood() {
        // Screens say "Picked" where the lifecycle says IN_TRANSIT. Same stage, and a return
        // must not be stranded over the choice of word.
        rr.setStatus("PICKED");

        assertDoesNotThrow(() -> service.updateStatus(1L, ReturnRequestService.RECEIVED));
        assertEquals(ReturnRequestService.RECEIVED, rr.getStatus());
    }

    @Test
    void anUnknownStatusIsRefusedRatherThanWritten() {
        assertThrows(ReturnRequestService.InvalidReturnTransitionException.class,
                () -> service.updateStatus(1L, "DEFINITELY_NOT_A_STATUS"));
        assertEquals(ReturnRequestService.REQUESTED, rr.getStatus());
    }
}
