package net.yura.shithead.uicomponents;

import net.yura.mobile.gui.Animation;
import net.yura.mobile.gui.DesktopPane;
import net.yura.shithead.client.HeadlessRunner;
import net.yura.shithead.client.ShitHeadApplication;
import net.yura.shithead.common.Player;
import net.yura.shithead.common.ShitheadGame;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies pixel positions of every card after the initial deal animation
 * completes in a 5-player game.
 *
 * Screen: 320 x 560. Display density 1.0 (XULLoader.adjustSizeToDensity(n) == n).
 *
 * All expected coordinates are pre-calculated from the layout algorithm so
 * that the test body contains no runtime maths.
 */
class GameViewTest {

    // X offsets of 3 cards relative to their hand centre (spacing=2 between cards)
    // handWidth = 3*44 + 2*2 = 136, startX = -136/2 = -68, step = CARD_W+2 = 46
    private static final int[] THREE_CARD_X_OFFSETS = { -68, -22, 24 };

    // Vertical gap from hand centre to first card row: (CARD_H + 2*overlap) / 2 = (80+40)/2 = 60
    private static final int Y_START_OFFSET = 60;

    // Vertical gap between card layers (overlap = CARD_H/4 = 20)
    private static final int LAYER_GAP = 20;

    // Screen dimensions
    private static final int SCREEN_W = 320;
    private static final int SCREEN_H = 560;

    // -------------------------------------------------------------------------
    // Local player (alice) — bottom of the screen
    //
    // Hand centre y = SCREEN_H - padding(2) - extraHandHeight(0) - CARD_H(80)
    //                 - upCardsOffset(20) - handCardsOffset(40) + Y_START_OFFSET(60)
    //               = 560 - 2 - 0 - 80 - 20 - 40 + 60 = 478
    // yCardsStart   = 478 - 60 = 418
    // -------------------------------------------------------------------------
    private static final int LOCAL_PLAYER_CX = 160;  // screen centre X
    private static final int LOCAL_PLAYER_Y_DOWN = 418;
    private static final int LOCAL_PLAYER_Y_UP = 438;  // 418 + LAYER_GAP
    private static final int LOCAL_PLAYER_Y_HAND = 478;  // 418 + LAYER_GAP + CARD_H/2 (not-yet-ready offset)

    // -------------------------------------------------------------------------
    // Remote player hand centres for a 5-player game on 320x560
    //
    // angle = π/2 + 0.2π + 0.32π*position  (radiusX=145, radiusY=190)
    // handCentreX = 160 + (int)(145 * cos(angle))
    // handCentreY = 280 + (int)(190 * sin(angle))
    //
    //   bob   position=1 → angle≈1.02π → centre ( 16, 269)
    //   carol position=2 → angle≈1.34π → centre ( 91, 114)
    //   dave  position=3 → angle≈1.66π → centre (229, 114)
    //   eve   position=4 → angle≈1.98π → centre (304, 269)
    // -------------------------------------------------------------------------
    private static final String[] REMOTE_NAMES    = { "bob",  "carol", "dave",  "eve"  };
    private static final int[]    REMOTE_CENTRE_X = {  16,     91,     229,     304   };
    private static final int[]    REMOTE_CENTRE_Y = { 269,    114,     114,     269   };

    // -------------------------------------------------------------------------
    // Deck: 3 visible cards stacked at x=115 (=160 - CARD_W - padding/2 = 160-44-1)
    //
    // lowestY  = ALICE_Y_DOWN - adjustSizeToDensity(20) - padding = 418 - 20 - 2 = 396
    // stackH   = CARD_H + (3-1)*padding = 80 + 4 = 84
    // yStart   = min(lowestY - stackH, CENTER_Y - stackH/2) = min(312, 238) = 238
    // -------------------------------------------------------------------------
    private static final int   DECK_X  = 115;
    private static final int[] DECK_YS = { 238, 240, 242 };  // yStart + i*padding

    @BeforeAll
    static void setupSwingMeEnvironment() throws Exception {
        HeadlessRunner.runApplication(ShitHeadApplication.class);
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> !DesktopPane.getDesktopPane().getAllFrames().isEmpty());
        EventQueue.invokeAndWait(new Thread());
    }

    @Test
    void testInitialCardPositionsAfterAnimation() throws Exception {
        ShitheadGame game = new ShitheadGame(Arrays.asList("alice", "bob", "carol", "dave", "eve"));
        game.deal();
        assertLayout(game);
    }

    /**
     * With two decks the deck is larger but each player still holds 3 down/up/hand cards, so
     * all pixel positions are identical to the single-deck case.  The important thing this test
     * checks is that duplicate cards (same rank+suit from each deck) each get their own UICard
     * and end up at the correct, distinct position.
     */
    @Test
    void testInitialCardPositionsAfterAnimation_twoDecks() throws Exception {
        ShitheadGame game = new ShitheadGame(Arrays.asList("alice", "bob", "carol", "dave", "eve"), 2);
        game.deal();
        assertLayout(game);
    }

    private static void assertLayout(ShitheadGame game) throws Exception {
        GameView view = new GameView();
        view.setGame(game);
        view.setMyUsername("alice");
        view.setSize(SCREEN_W, SCREEN_H); // triggers doLayout() → layoutCards()

        Animation.FPS = 1000; // speed up animation thread
        view.doLayout();

        await().atMost(10, TimeUnit.SECONDS).until(() -> !anyCardMoving(game, view));

        // Local player
        assertPlayerCards("alice", view.getPlayerHand("alice"),
                LOCAL_PLAYER_CX, LOCAL_PLAYER_Y_DOWN, LOCAL_PLAYER_Y_UP, LOCAL_PLAYER_Y_HAND);

        // Remote players
        for (int i = 0; i < REMOTE_NAMES.length; i++) {
            int yStart = REMOTE_CENTRE_Y[i] - Y_START_OFFSET;
            assertPlayerCards(REMOTE_NAMES[i], view.getPlayerHand(REMOTE_NAMES[i]),
                    REMOTE_CENTRE_X[i], yStart, yStart + LAYER_GAP, yStart + LAYER_GAP * 2);
        }

        // Deck
        List<UICard> deckCards = view.getDeckAndWasteCards();
        assertEquals(3, deckCards.size(), "deck visible card count");
        for (int i = 0; i < deckCards.size(); i++) {
            assertEquals(CardLocation.DECK, deckCards.get(i).getLocation());
            assertCardAt("deck[" + i + "]", deckCards.get(i), DECK_X, DECK_YS[i]);
        }

        // Every UICard across all player hands must be a distinct object — if two cards share a
        // UICard the one-UICard-per-card invariant (needed for correct positioning) is broken.
        assertAllUICardsUnique(game, view);
    }

    private static void assertPlayerCards(String name, PlayerHand hand,
                                           int cx, int downY, int upY, int handY) {
        assertLayer(name + " down", hand.getUiCards(CardLocation.DOWN_CARDS), cx, downY);
        assertLayer(name + " up",   hand.getUiCards(CardLocation.UP_CARDS),   cx, upY);
        assertLayer(name + " hand", hand.getUiCards(CardLocation.HAND),       cx, handY);
    }

    private static void assertLayer(String label, List<UICard> cards, int cx, int expectedY) {
        assertEquals(3, cards.size(), label + " count");
        for (int i = 0; i < cards.size(); i++) {
            assertCardAt(label + "[" + i + "]", cards.get(i), cx + THREE_CARD_X_OFFSETS[i], expectedY);
        }
    }

    private static void assertCardAt(String label, UICard card, int expectedX, int expectedY) {
        assertEquals(expectedX, card.getX(), label + " x");
        assertEquals(expectedY, card.getY(), label + " y");
    }

    private static void assertAllUICardsUnique(ShitheadGame game, GameView view) {
        IdentityHashMap<UICard, String> seen = new IdentityHashMap<>();
        List<UICard> allCards = new ArrayList<>(view.getDeckAndWasteCards());
        for (Player player : game.getPlayers()) {
            PlayerHand hand = view.getPlayerHand(player.getName());
            if (hand != null) {
                allCards.addAll(hand.getUiCards());
            }
        }
        for (UICard card : allCards) {
            String prev = seen.put(card, card.toString());
            if (prev != null) {
                fail("UICard used for multiple cards: " + card);
            }
        }
    }

    private static boolean anyCardMoving(ShitheadGame game, GameView view) {
        for (Player player : game.getPlayers()) {
            PlayerHand hand = view.getPlayerHand(player.getName());
            if (hand == null) continue;
            for (UICard card : hand.getUiCards()) {
                if (card.moving()) return true;
            }
        }
        return false;
    }
}
