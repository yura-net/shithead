package net.yura.shithead.common;

import net.yura.cardsengine.Card;
import net.yura.cardsengine.CardDeckEmptyException;
import net.yura.cardsengine.Deck;
import net.yura.cardsengine.Rank;
import net.yura.cardsengine.Suit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import java.util.Stack;
import java.util.Vector;
import static org.junit.jupiter.api.Assertions.*;

class ShitheadGameTest {

    private ShitheadGame game;
    private Player p1;
    private Player p2;

    @BeforeEach
    void setUp() {
        game = new ShitheadGame(2);
        assertNull(game.getCurrentPlayer());
        try {
            // Get players for easier testing
            List<Player> players = game.getPlayers();
            p1 = players.get(0);
            p2 = players.get(1);

            // Inject an empty deck to prevent hand refilling, which would break the tests
            java.lang.reflect.Field deckField = ShitheadGame.class.getDeclaredField("deck");
            deckField.setAccessible(true);
            Deck emptyDeck = new Deck(1);
            try {
                while (true) {
                    emptyDeck.dealCard();
                }
            } catch (CardDeckEmptyException e) {
                // Deck is now empty, which is what we want.
            }
            deckField.set(game, emptyDeck);

        } catch (Exception e) {
            fail("Failed to set up test with reflection: " + e.getMessage());
        }
    }

    @Test
    void testGameFlowAndWinCondition() {
        // All players are ready
        game.playerReady(p1);
        game.playerReady(p2);

        // Setup P1 to start
        p1.getHand().add(Card.getCardByRankSuit(Rank.THREE, Suit.SPADES));
        p1.getUpcards().add(Card.getCardByRankSuit(Rank.QUEEN, Suit.HEARTS));

        // Setup P2
        p2.getHand().add(Card.getCardByRankSuit(Rank.FOUR, Suit.HEARTS));
        p2.getUpcards().add(Card.getCardByRankSuit(Rank.KING, Suit.CLUBS));
        p2.getDowncards().add(Card.getCardByRankSuit(Rank.ACE, Suit.DIAMONDS));

        // P1 plays from hand
        assertTrue(game.playCards(Collections.singletonList(Card.getCardByRankSuit(Rank.THREE, Suit.SPADES))));
        assertTrue(p1.getHand().isEmpty());
        assertEquals(p2, game.getCurrentPlayer());

        // P2 plays from hand
        assertTrue(game.playCards(Collections.singletonList(Card.getCardByRankSuit(Rank.FOUR, Suit.HEARTS))));
        assertTrue(p2.getHand().isEmpty());
        assertEquals(p1, game.getCurrentPlayer());

        // P1 plays from upcards and wins
        assertTrue(game.playCards(Collections.singletonList(Card.getCardByRankSuit(Rank.QUEEN, Suit.HEARTS))));
        assertTrue(p1.getUpcards().isEmpty());
        assertTrue(game.isFinished(), "Game should be finished after P1 plays their last upcard");
    }

    @Test
    void testPickUpWastePile() {
        // Setup so P1 plays a King and P2 must pick it up.
        // P1 must start
        p1.getHand().add(Card.getCardByRankSuit(Rank.THREE, Suit.CLUBS)); // To start
        p1.getHand().add(Card.getCardByRankSuit(Rank.KING, Suit.CLUBS));
        p1.getUpcards().add(Card.getCardByRankSuit(Rank.ACE, Suit.SPADES));

        // P2 has a high card and an unplayable card
        p2.getHand().add(Card.getCardByRankSuit(Rank.FOUR, Suit.HEARTS)); // to not start
        p2.getHand().add(Card.getCardByRankSuit(Rank.SIX, Suit.DIAMONDS));

        game.playerReady(p1);
        game.playerReady(p2); // P1 starts.

        // P1 plays the 3.
        game.playCards(Collections.singletonList(Card.getCardByRankSuit(Rank.THREE, Suit.CLUBS)));
        // P2's turn. Plays the 4.
        game.playCards(Collections.singletonList(Card.getCardByRankSuit(Rank.FOUR, Suit.HEARTS)));
        // P1's turn. Plays the King.
        game.playCards(Collections.singletonList(Card.getCardByRankSuit(Rank.KING, Suit.CLUBS)));

        // P2's turn. Top card is King. P2 has a 6. Cannot play.
        assertFalse(game.playCards(Collections.singletonList(Card.getCardByRankSuit(Rank.SIX, Suit.DIAMONDS))));

        // P2 picks up the pile
        int cardsInHandBefore = p2.getHand().size();
        int cardsInWasteBefore = game.getWastePile().size();
        game.pickUpWastePile();

        assertEquals(cardsInHandBefore + cardsInWasteBefore, p2.getHand().size());
        assertEquals(0, game.getWastePile().size());
        assertEquals(p1, game.getCurrentPlayer());
    }

    @Test
    void testPlayFromDowncards() {
        // Ensure hands and upcards are empty for both players
        p1.getHand().clear();
        p1.getUpcards().clear();
        p2.getHand().clear();
        p2.getUpcards().clear();

        p1.getDowncards().add(Card.getCardByRankSuit(Rank.ACE, Suit.DIAMONDS));
        p2.getDowncards().add(Card.getCardByRankSuit(Rank.KING, Suit.HEARTS));


        // All players are ready
        game.playerReady(p1);
        game.playerReady(p2);
        // Since hand and upcards are empty, the first player will be p1 (index 0) by default.
        // No need to set it manually.

        // P1 plays their downcard (blind)
        assertTrue(game.playCards(Collections.singletonList(Card.getCardByRankSuit(Rank.ACE, Suit.DIAMONDS))));
        assertTrue(p1.getDowncards().isEmpty());
        assertEquals(1, game.getWastePile().size());
    }

    @Test
    void testPlaySpecialCards() {
        // All players are ready
        game.playerReady(p1);
        game.playerReady(p2);

        // Give players upcards so they don't win immediately
        p1.getUpcards().add(Card.getCardByRankSuit(Rank.ACE, Suit.HEARTS));
        p2.getUpcards().add(Card.getCardByRankSuit(Rank.ACE, Suit.CLUBS));

        // Test playing a Two on any card
        p1.getHand().add(Card.getCardByRankSuit(Rank.THREE, Suit.SPADES));
        p2.getHand().add(Card.getCardByRankSuit(Rank.FOUR, Suit.HEARTS));
        game.playCards(Collections.singletonList(Card.getCardByRankSuit(Rank.THREE, Suit.SPADES)));

        p2.getHand().add(Card.getCardByRankSuit(Rank.TWO, Suit.CLUBS));
        assertTrue(game.playCards(Collections.singletonList(Card.getCardByRankSuit(Rank.TWO, Suit.CLUBS))));
        assertEquals(2, game.getWastePile().size());

        // Test playing a Ten to burn the pile and play again
        p1.getHand().add(Card.getCardByRankSuit(Rank.TEN, Suit.DIAMONDS));
        assertTrue(game.playCards(Collections.singletonList(Card.getCardByRankSuit(Rank.TEN, Suit.DIAMONDS))));
        assertEquals(0, game.getWastePile().size());
        assertEquals(p1, game.getCurrentPlayer(), "Player should play again after burning the pile");
    }

    @Test
    public void testPlayDeckCardSuccess() {
        // given
        Deck deck = new Deck(1);
        ShitheadGame game = new ShitheadGame(2, deck);
        game.deal();
        game.playerReady(game.getPlayers().get(0));
        game.playerReady(game.getPlayers().get(1));
        Player originalPlayer = game.getCurrentPlayer();
        Player otherPlayer = game.getPlayers().stream().filter(p -> p != originalPlayer).findFirst().get();

        Vector cards = deck.getCards();
        Stack stack = new Stack();
        stack.addAll(cards);
        // Make sure the top card is playable
        stack.set(stack.size() - 1, Card.getCardByRankSuit(Rank.ACE, Suit.SPADES));
        cards.clear();
        cards.addAll(stack);

        Card topCard = (Card) deck.getCards().get(deck.getCards().size() - 1);
        int initialDeckSize = deck.getCards().size();

        // when
        String mutation = CommandParser.getMutationCommand(game, "play deck");
        assertEquals("play deck " + topCard, mutation);

        // then
        new CommandParser().execute(game, mutation);
        assertEquals(initialDeckSize - 1, deck.getCards().size());
        assertTrue(game.getWastePile().contains(topCard));
        assertEquals(otherPlayer, game.getCurrentPlayer());
    }

    @Test
    public void testPlayDeckCardFail() {
        // given
        ShitheadGame game = new ShitheadGame(2);
        game.deal();
        game.playerReady(game.getPlayers().get(0));
        game.playerReady(game.getPlayers().get(1));
        Player originalPlayer = game.getCurrentPlayer();
        Player otherPlayer = game.getPlayers().stream().filter(p -> p != originalPlayer).findFirst().get();

        // Make the top card of the deck unplayable
        game.setWastePile(new java.util.ArrayList<>(Collections.singletonList(Card.getCardByRankSuit(Rank.KING, Suit.SPADES))));
        Deck deck = game.getDeck();
        Vector cards = deck.getCards();
        Stack stack = new Stack();
        stack.addAll(cards);
        stack.set(stack.size() - 1, Card.getCardByRankSuit(Rank.THREE, Suit.CLUBS));
        cards.clear();
        cards.addAll(stack);

        Card topCard = (Card) deck.getCards().get(deck.getCards().size() - 1);
        int initialDeckSize = deck.getCards().size();
        int initialHandSize = originalPlayer.getHand().size();

        // when
        String mutation = CommandParser.getMutationCommand(game, "play deck");
        assertEquals("play deck " + topCard, mutation);

        // then
        new CommandParser().parse(game, mutation);
        assertEquals(initialDeckSize - 1, deck.getCards().size());
        assertEquals(0, game.getWastePile().size());
        assertEquals(initialHandSize + 2, originalPlayer.getHand().size()); // +1 for the card from the deck, +1 for the card from the waste pile
        assertTrue(originalPlayer.getHand().contains(topCard));
        assertEquals(otherPlayer, game.getCurrentPlayer());
    }

    @Test
    void testChooseFirstPlayer() {
        // given
        p1.getUpcards().add(Card.getCardByRankSuit(Rank.FOUR, Suit.CLUBS));
        p2.getUpcards().add(Card.getCardByRankSuit(Rank.THREE, Suit.DIAMONDS));

        // when
        game.playerReady(p1);
        game.playerReady(p2);

        // then
        assertEquals(p2, game.getCurrentPlayer());
    }

    @Test
    void testRearrangeCardsMaintainsPosition() {
        // given
        Card h1 = Card.getCardByRankSuit(Rank.ACE, Suit.SPADES);
        Card h2 = Card.getCardByRankSuit(Rank.TWO, Suit.SPADES);
        Card h3 = Card.getCardByRankSuit(Rank.THREE, Suit.SPADES);
        p1.getHand().addAll(List.of(h1, h2, h3));

        Card u1 = Card.getCardByRankSuit(Rank.FOUR, Suit.CLUBS);
        Card u2 = Card.getCardByRankSuit(Rank.FIVE, Suit.CLUBS);
        Card u3 = Card.getCardByRankSuit(Rank.SIX, Suit.CLUBS);
        p1.getUpcards().addAll(List.of(u1, u2, u3));

        // when
        game.rearrangeCards(p1, h2, u3); // Swap 2nd hand card with 3rd up card

        // then
        assertEquals(3, p1.getHand().size());
        assertEquals(3, p1.getUpcards().size());

        // Check that the cards are in the correct new positions
        assertEquals(u3, p1.getHand().get(1));
        assertEquals(h2, p1.getUpcards().get(2));

        // Check that other cards have not moved
        assertEquals(h1, p1.getHand().get(0));
        assertEquals(h3, p1.getHand().get(2));
        assertEquals(u1, p1.getUpcards().get(0));
        assertEquals(u2, p1.getUpcards().get(1));
    }

    @Test
    void testRearrangeCardsOrderIndependent() {
        // given
        Card h1 = Card.getCardByRankSuit(Rank.ACE, Suit.DIAMONDS);
        Card h2 = Card.getCardByRankSuit(Rank.TWO, Suit.DIAMONDS);
        Card h3 = Card.getCardByRankSuit(Rank.THREE, Suit.DIAMONDS);
        p1.getHand().addAll(List.of(h1, h2, h3));

        Card u1 = Card.getCardByRankSuit(Rank.SEVEN, Suit.HEARTS);
        Card u2 = Card.getCardByRankSuit(Rank.EIGHT, Suit.HEARTS);
        Card u3 = Card.getCardByRankSuit(Rank.NINE, Suit.HEARTS);
        p1.getUpcards().addAll(List.of(u1, u2, u3));

        // when
        game.rearrangeCards(p1, u2, h1); // Swap 2nd up card with 1st hand card (arguments reversed)

        // then
        assertEquals(3, p1.getHand().size());
        assertEquals(3, p1.getUpcards().size());

        // Check that the cards are in the correct new positions
        assertEquals(u2, p1.getHand().get(0));
        assertEquals(h1, p1.getUpcards().get(1));

        // Check that other cards have not moved
        assertEquals(h2, p1.getHand().get(1));
        assertEquals(h3, p1.getHand().get(2));
        assertEquals(u1, p1.getUpcards().get(0));
        assertEquals(u3, p1.getUpcards().get(2));
    }

    @Test
    void testCannotRearrangeCardsWhenReady() {
        // given
        Card handCard = Card.getCardByRankSuit(Rank.ACE, Suit.SPADES);
        Card upCard = Card.getCardByRankSuit(Rank.KING, Suit.HEARTS);
        p1.getHand().add(handCard);
        p1.getUpcards().add(upCard);

        // when
        game.playerReady(p1);

        // then
        assertThrows(IllegalStateException.class, () -> {
            game.rearrangeCards(p1, handCard, upCard);
        });
    }

    @Test
    void testPlayAceOnKing() {
        // All players are ready
        game.playerReady(p1);
        game.playerReady(p2);

        // Set up the waste pile with a King on top
        game.setWastePile(new java.util.ArrayList<>(Collections.singletonList(Card.getCardByRankSuit(Rank.KING, Suit.SPADES))));

        // Give player 1 an Ace
        Card ace = Card.getCardByRankSuit(Rank.ACE, Suit.HEARTS);
        p1.getHand().add(ace);

        // Attempt to play the Ace on the King
        assertTrue(game.playCards(Collections.singletonList(ace)), "An Ace should be playable on a King");
    }

    @Test
    void testSevenGoLow_lowCardIsAccepted() {
        game.setSevenGoLow(true);
        game.playerReady(p1);
        game.playerReady(p2);

        // Waste pile topped with a Seven
        game.setWastePile(new java.util.ArrayList<>(Collections.singletonList(Card.getCardByRankSuit(Rank.SEVEN, Suit.SPADES))));

        // Give p1 a Five (lower than 7) – must be accepted
        Card five = Card.getCardByRankSuit(Rank.FIVE, Suit.HEARTS);
        p1.getHand().add(five);
        p1.getUpcards().add(Card.getCardByRankSuit(Rank.ACE, Suit.CLUBS));

        assertTrue(game.playCards(Collections.singletonList(five)), "A Five should be playable on a Seven when sevenGoLow is enabled");
        assertEquals(2, game.getWastePile().size());
    }

    @Test
    void testSevenGoLow_equalCardIsAccepted() {
        game.setSevenGoLow(true);
        game.playerReady(p1);
        game.playerReady(p2);

        game.setWastePile(new java.util.ArrayList<>(Collections.singletonList(Card.getCardByRankSuit(Rank.SEVEN, Suit.SPADES))));

        Card seven = Card.getCardByRankSuit(Rank.SEVEN, Suit.HEARTS);
        p1.getHand().add(seven);
        p1.getUpcards().add(Card.getCardByRankSuit(Rank.ACE, Suit.CLUBS));

        assertTrue(game.playCards(Collections.singletonList(seven)), "A Seven should be playable on a Seven when sevenGoLow is enabled");
    }

    @Test
    void testSevenGoLow_higherCardIsRejected() {
        game.setSevenGoLow(true);
        game.playerReady(p1);
        game.playerReady(p2);

        game.setWastePile(new java.util.ArrayList<>(Collections.singletonList(Card.getCardByRankSuit(Rank.SEVEN, Suit.SPADES))));

        // Give p1 a Nine (higher than 7) – must be rejected
        Card nine = Card.getCardByRankSuit(Rank.NINE, Suit.DIAMONDS);
        p1.getHand().add(nine);

        assertFalse(game.playCards(Collections.singletonList(nine)), "A Nine should NOT be playable on a Seven when sevenGoLow is enabled");
        // Waste pile unchanged
        assertEquals(1, game.getWastePile().size());
        assertEquals(Rank.SEVEN, game.getWastePile().get(0).getRank());
    }

    @Test
    void testSevenGoLow_aceIsRejected() {
        game.setSevenGoLow(true);
        game.playerReady(p1);
        game.playerReady(p2);

        game.setWastePile(new java.util.ArrayList<>(Collections.singletonList(Card.getCardByRankSuit(Rank.SEVEN, Suit.CLUBS))));

        // Ace ranks highest (14) and must be rejected
        Card ace = Card.getCardByRankSuit(Rank.ACE, Suit.SPADES);
        p1.getHand().add(ace);

        assertFalse(game.playCards(Collections.singletonList(ace)), "An Ace should NOT be playable on a Seven when sevenGoLow is enabled");
    }

    @Test
    void testSevenGoLow_twoIsAlwaysPlayable() {
        game.setSevenGoLow(true);
        game.playerReady(p1);
        game.playerReady(p2);

        game.setWastePile(new java.util.ArrayList<>(Collections.singletonList(Card.getCardByRankSuit(Rank.SEVEN, Suit.HEARTS))));

        Card two = Card.getCardByRankSuit(Rank.TWO, Suit.DIAMONDS);
        p1.getHand().add(two);
        p1.getUpcards().add(Card.getCardByRankSuit(Rank.ACE, Suit.CLUBS));

        assertTrue(game.playCards(Collections.singletonList(two)), "A Two should always be playable, even on a Seven with sevenGoLow enabled");
    }

    @Test
    void testSevenGoLow_tenIsAlwaysPlayable() {
        game.setSevenGoLow(true);
        game.playerReady(p1);
        game.playerReady(p2);

        game.setWastePile(new java.util.ArrayList<>(Collections.singletonList(Card.getCardByRankSuit(Rank.SEVEN, Suit.DIAMONDS))));

        Card ten = Card.getCardByRankSuit(Rank.TEN, Suit.CLUBS);
        p1.getHand().add(ten);
        p1.getUpcards().add(Card.getCardByRankSuit(Rank.ACE, Suit.CLUBS));

        assertTrue(game.playCards(Collections.singletonList(ten)), "A Ten should always be playable (burns the pile), even on a Seven with sevenGoLow enabled");
        assertEquals(0, game.getWastePile().size(), "Pile should be burned after playing Ten");
    }

    @Test
    void testPlayerWinsByPlayingLastUpcardEvenWithNonEmptyDeck() {
        // Per the rules, drawing from the deck only happens when playing from the hand.
        // Playing a player's last upcard must end the game even when the deck still has cards.
        Deck fullDeck = new Deck(1);
        ShitheadGame g = new ShitheadGame(2, fullDeck);
        Player a = g.getPlayers().get(0);
        Player b = g.getPlayers().get(1);

        a.getUpcards().add(Card.getCardByRankSuit(Rank.QUEEN, Suit.HEARTS));
        b.getHand().add(Card.getCardByRankSuit(Rank.FOUR, Suit.HEARTS));

        g.playerReady(a);
        g.playerReady(b);
        g.setCurrentPlayer(0);

        assertTrue(g.playCards(Collections.singletonList(Card.getCardByRankSuit(Rank.QUEEN, Suit.HEARTS))));

        assertTrue(a.getHand().isEmpty(), "Hand must remain empty - playing from upcards must not refill from deck");
        assertTrue(a.getUpcards().isEmpty());
        assertTrue(a.getDowncards().isEmpty());
        assertTrue(g.isFinished(), "Game should be finished after a plays their last upcard");
    }

    @Test
    void testPlayerWinsByPlayingLastDowncardEvenWithNonEmptyDeck() {
        Deck fullDeck = new Deck(1);
        ShitheadGame g = new ShitheadGame(2, fullDeck);
        Player a = g.getPlayers().get(0);
        Player b = g.getPlayers().get(1);

        Card downCard = Card.getCardByRankSuit(Rank.QUEEN, Suit.HEARTS);
        a.getDowncards().add(downCard);
        b.getHand().add(Card.getCardByRankSuit(Rank.FOUR, Suit.HEARTS));

        g.playerReady(a);
        g.playerReady(b);
        g.setCurrentPlayer(0);

        assertTrue(g.playCards(Collections.singletonList(downCard)));

        assertTrue(a.getHand().isEmpty(), "Hand must remain empty - playing from downcards must not refill from deck");
        assertTrue(a.getDowncards().isEmpty());
        assertTrue(g.isFinished(), "Game should be finished after a plays their last downcard");
    }

    @Test
    void testPlayingFromHandStillRefillsFromDeck() {
        // Sanity check: refilling must still happen when playing from the hand.
        Deck fullDeck = new Deck(1);
        ShitheadGame g = new ShitheadGame(2, fullDeck);
        Player a = g.getPlayers().get(0);
        Player b = g.getPlayers().get(1);

        Card threeOfClubs = Card.getCardByRankSuit(Rank.THREE, Suit.CLUBS);
        a.getHand().add(threeOfClubs);
        a.getUpcards().add(Card.getCardByRankSuit(Rank.QUEEN, Suit.HEARTS));
        b.getHand().add(Card.getCardByRankSuit(Rank.FOUR, Suit.HEARTS));

        g.playerReady(a);
        g.playerReady(b);
        g.setCurrentPlayer(0);

        int deckSizeBefore = fullDeck.getCards().size();
        assertTrue(g.playCards(Collections.singletonList(threeOfClubs)));

        assertEquals(3, a.getHand().size(), "Hand must be refilled to 3 after playing from hand");
        assertEquals(deckSizeBefore - 3, fullDeck.getCards().size());
    }

    @Test
    void testSevenGoLow_ruleDoesNotApplyWhenDisabled() {
        // sevenGoLow defaults to false – high card on 7 must be accepted normally
        game.playerReady(p1);
        game.playerReady(p2);

        game.setWastePile(new java.util.ArrayList<>(Collections.singletonList(Card.getCardByRankSuit(Rank.SEVEN, Suit.SPADES))));

        Card king = Card.getCardByRankSuit(Rank.KING, Suit.HEARTS);
        p1.getHand().add(king);
        p1.getUpcards().add(Card.getCardByRankSuit(Rank.ACE, Suit.CLUBS));

        assertTrue(game.playCards(Collections.singletonList(king)), "A King should be playable on a Seven when sevenGoLow is disabled");
    }
}
