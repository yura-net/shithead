package net.yura.shithead.uicomponents;

import net.yura.cardsengine.Card;
import net.yura.cardsengine.Deck;
import net.yura.mobile.gui.Animation;
import net.yura.mobile.gui.DesktopPane;
import net.yura.shithead.client.HeadlessRunner;
import net.yura.shithead.client.ShitHeadApplication;
import net.yura.shithead.common.Player;
import net.yura.shithead.common.ShitheadGame;
import net.yura.shithead.common.json.SerializerUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        ShitheadGame game = newGame(1);
        assertLayout(game);
    }

    private static ShitheadGame newGame(int decks) {
        Deck deck = new Deck(decks);
        deck.setRandom(new Random(123));
        ShitheadGame game = new ShitheadGame(Arrays.asList("alice", "bob", "carol", "dave", "eve"), deck);
        game.deal();
        return game;
    }

    private static void assertLayout(ShitheadGame game) throws Exception {
        GameView view = new GameView();
        view.setGame(game);
        view.setMyUsername("alice");
        view.setSize(SCREEN_W, SCREEN_H); // triggers doLayout() → layoutCards()

        Animation.FPS = 1000; // speed up animation thread

        assertLayout(game, view);
        // we want to make sure its still correct after doing another layout
        assertLayout(game, view);
    }

    private static void assertLayout(ShitheadGame game, GameView view) {
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

    @Test
    void testGetUnusedCards_AllMatched() {
        Card c1 = SerializerUtil.cardFromString("7H");
        Card c2 = SerializerUtil.cardFromString("8S");
        List<UICard> source = new ArrayList<>(Arrays.asList(
                new UICard(c1, CardLocation.HAND, true, 0, 0),
                new UICard(c2, CardLocation.HAND, true, 0, 0)
        ));
        List<Card> actual = Arrays.asList(c1, c2);

        List<UICard> result = GameView.getUnusedCards(source, actual);

        assertTrue(result.isEmpty());
        assertEquals(2, source.size());
    }

    @Test
    void testGetUnusedCards_SomeUnmatched() {
        Card c1 = SerializerUtil.cardFromString("7H");
        Card c2 = SerializerUtil.cardFromString("8S");
        Card c3 = SerializerUtil.cardFromString("9D");
        UICard ui1 = new UICard(c1, CardLocation.HAND, true, 0, 0);
        UICard ui2 = new UICard(c2, CardLocation.HAND, true, 0, 0);
        List<UICard> source = new ArrayList<>(Arrays.asList(ui1, ui2));
        List<Card> actual = Arrays.asList(c1, c3); // c2 is missing from actual, c3 is new

        List<UICard> result = GameView.getUnusedCards(source, actual);

        assertEquals(1, result.size());
        assertEquals(ui2, result.get(0));
        assertEquals(1, source.size());
        assertEquals(ui1, source.get(0));
    }

    @Test
    void testGetUnusedCards_NullCards() {
        Card c1 = SerializerUtil.cardFromString("7H");
        UICard ui1 = new UICard(c1, CardLocation.HAND, true, 0, 0);
        UICard uiNull1 = new UICard(null, CardLocation.DECK, false, 0, 0);
        UICard uiNull2 = new UICard(null, CardLocation.DECK, false, 0, 0);
        List<UICard> source = new ArrayList<>(Arrays.asList(ui1, uiNull1, uiNull2));

        // actual only has c1 and one unknown card. So one null UICard should be returned.
        List<Card> actual = Arrays.asList(c1, null);

        List<UICard> result = GameView.getUnusedCards(source, actual);

        assertEquals(1, result.size());
        assertEquals(uiNull1, result.get(0));
        assertEquals(2, source.size());
        assertTrue(source.contains(ui1));
        assertTrue(source.contains(uiNull2));
    }

    @Test
    void testGetUnusedCards_EmptyActual() {
        Card c1 = SerializerUtil.cardFromString("7H");
        UICard ui1 = new UICard(c1, CardLocation.HAND, true, 0, 0);
        List<UICard> source = new ArrayList<>(Arrays.asList(ui1));
        List<Card> actual = Arrays.asList();

        List<UICard> result = GameView.getUnusedCards(source, actual);

        assertEquals(1, result.size());
        assertEquals(ui1, result.get(0));
        assertTrue(source.isEmpty());
    }

    @Test
    void testGetUnusedCards_DoesNotReturnSameNullCardTwice() {
        Card c1 = SerializerUtil.cardFromString("7H");

        UICard ui1 = new UICard(c1, CardLocation.HAND, true, 0, 0);
        UICard uiNull1 = new UICard(null, CardLocation.DECK, false, 0, 0);
        UICard uiNull2 = new UICard(null, CardLocation.DECK, false, 0, 0);

        List<UICard> source = new ArrayList<>(Arrays.asList(
                ui1,
                uiNull1,
                uiNull2
        ));

        List<Card> actual = Collections.singletonList(c1);

        List<UICard> result = GameView.getUnusedCards(source, actual);

        assertEquals(2, result.size());

        // should contain both null cards
        assertTrue(result.contains(uiNull1));
        assertTrue(result.contains(uiNull2));

        // should not contain duplicates
        assertEquals(2, new HashSet<>(result).size());
    }

    @Test
    void testGetUnusedCardsCountsDuplicateSingletonCardsSeparately() {
        List<Card> cards = new ArrayList<>(new Deck(3).getCards());
        Card duplicateCard = cards.stream()
                .filter(card -> cards.stream().filter(other -> other == card).count() >= 3)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no card with three copies found"));
        Card otherCard = cards.stream()
                .filter(card -> card != duplicateCard)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no other card found"));

        List<UICard> source = new ArrayList<>(Arrays.asList(
                new UICard(duplicateCard, CardLocation.HAND, true, 0, 0),
                new UICard(duplicateCard, CardLocation.HAND, true, 0, 0),
                new UICard(duplicateCard, CardLocation.HAND, true, 0, 0),
                new UICard(otherCard, CardLocation.HAND, true, 0, 0)));
        List<Card> actual = Arrays.asList(duplicateCard, duplicateCard, otherCard);

        List<UICard> available = GameView.getUnusedCards(source, actual);

        assertEquals(1, available.size(), "one duplicate should be unused");
        assertEquals(duplicateCard, available.get(0).getCard(), "unused card");
        assertEquals(3, source.size(), "remaining source cards");
    }

    @Test
    void testGetUnusedCards_DuplicateUICard_OrderMatters() {
        Card c7H = SerializerUtil.cardFromString("7H");
        Card c8S = SerializerUtil.cardFromString("8S");

        // KEY: 8S comes FIRST in the list, then the two 7H duplicates
        UICard ui8S = new UICard(c8S, CardLocation.HAND, true, 0, 0);
        UICard ui7H_1 = new UICard(c7H, CardLocation.HAND, true, 0, 0);
        UICard ui7H_2 = new UICard(c7H, CardLocation.HAND, true, 0, 0);

        List<UICard> source = new ArrayList<>(Arrays.asList(ui8S, ui7H_1, ui7H_2));
        List<Card> actual = Arrays.asList(c7H, c8S); // one of each

        List<UICard> result = GameView.getUnusedCards(source, actual);

        assertEquals(1, result.size());
        // The freed card must be the duplicate 7H, not the 8S
        assertEquals(c7H, result.get(0).getCard());
        // 8S must still be in source
        assertTrue(source.stream().anyMatch(c -> Objects.equals(c8S, c.getCard())));
    }
}
