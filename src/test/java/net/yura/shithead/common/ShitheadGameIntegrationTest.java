package net.yura.shithead.common;

import net.yura.cardsengine.Card;
import net.yura.cardsengine.Deck;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

public class ShitheadGameIntegrationTest {

    @Test
    public void testFullGameWithStateVerificationAtEachStep() {
        // Setup for the 2-player game, now local to the test method.
        Deck deck = new Deck(1);
        deck.setRandom(new Random(2024L));
        ShitheadGame game = new ShitheadGame(2, deck);
        game.deal();

        // --- Card Swapping Phase ---
        // Player 1 swaps a card from hand with an up-card.
        Player p1 = game.getPlayers().get(0);
        game.rearrangeCards(p1, p1.getHand().get(0), p1.getUpcards().get(0));

        // Both players are ready to start.
        game.playerReady(p1);
        game.playerReady(game.getPlayers().get(1));


        int maxTurns = 200;
        int turn = 0;

        // --- Game Loop ---
        // The test will loop until the game is finished, simulating each player's turn.
        while (!game.isFinished() && turn < maxTurns) {
            Player currentPlayer = game.getCurrentPlayer();
            Player otherPlayer = getOtherPlayer(game, currentPlayer);
            int initialHandSize = currentPlayer.getHand().size();
            int initialWastePileSize = game.getWastePile().size();

            // --- Player Logic: Find a valid card to play ---
            // The AI finds the best card from hand or up-cards.
            Card cardToPlay = AutoPlay.findBestVisibleCard(game);

            if (cardToPlay != null) {
                // --- Action: Play a valid card ---
                game.playCards(Collections.singletonList(cardToPlay));

                // --- Verification: Check game state after playing ---
                if (game.getWastePile().isEmpty()) { // Pile was burned
                    assertEquals(0, game.getWastePile().size(), "Waste pile should be burned.");
                    assertEquals(currentPlayer, game.getCurrentPlayer(), "Player should get another turn after a burn.");
                } else {
                    assertEquals(initialWastePileSize + 1, game.getWastePile().size(), "Waste pile should increase by one.");
                    assertEquals(otherPlayer, game.getCurrentPlayer(), "Turn should advance to the next player.");
                }
            } else {
                // --- Action: No playable card found ---
                if (!currentPlayer.getHand().isEmpty() || !currentPlayer.getUpcards().isEmpty()) {
                    // --- Sub-Phase: Hand or Up-cards ---
                    // If the player has cards in hand or up-cards but none are playable, they must pick up the pile.
                    game.pickUpWastePile();
                    assertEquals(0, game.getWastePile().size(), "Waste pile should be empty after pickup.");
                    assertEquals(initialHandSize + initialWastePileSize, currentPlayer.getHand().size(), "Player's hand should contain the picked-up pile.");
                    assertEquals(otherPlayer, game.getCurrentPlayer(), "Turn should advance after picking up pile.");
                } else {
                    // --- Sub-Phase: Down-cards ---
                    // If hand and up-cards are empty, the player must play a down-card blindly.
                    Card downCard = currentPlayer.getDowncards().get(0);
                    boolean playSuccessful = game.playCards(Collections.singletonList(downCard));

                    if (!playSuccessful) {
                        // --- Verification: Check penalty for invalid down-card ---
                        assertEquals(0, game.getWastePile().size(), "Waste pile should be empty after penalty pickup.");
                        assertEquals(initialWastePileSize + 1, currentPlayer.getHand().size(), "Player's hand should contain the pile and the invalid down card.");
                        assertEquals(otherPlayer, game.getCurrentPlayer(), "Turn should advance after penalty.");
                    }
                }
            }
            turn++;
        }

        // --- Final Verification ---
        assertTrue(turn < maxTurns, "Game did not finish within the turn limit (" + maxTurns + "), possible infinite loop.");
        assertTrue(game.isFinished(), "Game should be finished.");
        assertEquals(1, game.getPlayers().size(), "There should be one loser left.");
    }

    private Player getOtherPlayer(ShitheadGame game, Player currentPlayer) {
        for (Player p : game.getPlayers()) {
            if (p != currentPlayer) {
                return p;
            }
        }
        return null; // Should not happen in a 2-player game
    }

    @Test
    public void testCardSwappingPhase() {
        // --- Setup: Create a 2-player game with a predictable deck ---
        Deck deck = new Deck(1);
        deck.setRandom(new Random(2023L));
        ShitheadGame game = new ShitheadGame(2, deck);
        game.deal();

        Player p1 = game.getPlayers().get(0);

        // --- Verification: Check initial state ---
        assertTrue(game.isRearranging(), "Game should start in the REARRANGING state.");
        assertFalse(game.isPlaying(), "Game should not be in the PLAYING state yet.");

        // --- Action & Verification: Perform a valid swap ---
        Card handCard = p1.getHand().get(0);
        Card upCard = p1.getUpcards().get(0);

        game.rearrangeCards(p1, handCard, upCard);

        assertFalse(p1.getHand().contains(handCard), "The hand card should have been moved from the hand.");
        assertTrue(p1.getUpcards().contains(handCard), "The hand card should now be in the up-cards.");
        assertTrue(p1.getHand().contains(upCard), "The up-card should now be in the hand.");
        assertFalse(p1.getUpcards().contains(upCard), "The up-card should have been moved from the up-cards.");

        // --- Action & Verification: Attempt an invalid swap ---
        Card notOwnedCard = Card.getCardByRankSuit(net.yura.cardsengine.Rank.ACE, net.yura.cardsengine.Suit.SPADES);
        assertThrows(IllegalArgumentException.class, () -> {
            game.rearrangeCards(p1, notOwnedCard, p1.getUpcards().get(1));
        }, "Should not be able to swap a card the player does not own.");

        // --- Action: Transition to PLAYING state ---
        game.playerReady(p1);
        game.playerReady(game.getPlayers().get(1));

        // --- Verification: Check final state ---
        assertFalse(game.isRearranging(), "Game should no longer be in the REARRANGING state.");
        assertTrue(game.isPlaying(), "Game should now be in the PLAYING state.");

        // --- Action & Verification: Attempt swap after game has started ---
        assertThrows(IllegalStateException.class, () -> {
            game.rearrangeCards(p1, p1.getHand().get(0), p1.getUpcards().get(0));
        }, "Should not be able to swap cards after the game has started.");
    }

    @Test
    public void testFull3PlayerGameWithRuleBasedPlayer() {
        // This test has its own setup for a 3-player game.
        Deck deck = new Deck(1);
        deck.setRandom(new Random(2025L));
        ShitheadGame game = new ShitheadGame(3, deck);
        game.deal();

        // --- Card Swapping Phase for 3 players ---
        for (Player p : game.getPlayers()) {
            // As a simple strategy, swap the first hand card with the first up-card.
            if (!p.getHand().isEmpty() && !p.getUpcards().isEmpty()) {
                game.rearrangeCards(p, p.getHand().get(0), p.getUpcards().get(0));
            }
            game.playerReady(p);
        }

        int maxTurns = 500;
        int turn = 0;

        // --- Game Loop (3-Player) ---
        while (!game.isFinished() && turn < maxTurns) {
            Player currentPlayer = game.getCurrentPlayer();
            int initialHandSize = currentPlayer.getHand().size();
            int initialWastePileSize = game.getWastePile().size();

            // --- Player Logic: Find a valid card to play ---
            List<Card> cardsToPlay = AutoPlay.findBestVisibleCards(game);

            if (!cardsToPlay.isEmpty()) {
                // --- Action: Play a valid card ---
                Player playerBeforeMove = currentPlayer;
                game.playCards(cardsToPlay);
                Player playerAfterMove = game.getCurrentPlayer();

                // --- Verification: Check game state after playing ---
                if (playerAfterMove == playerBeforeMove) {
                    // Player got another turn, which means the pile was burned.
                    assertEquals(0, game.getWastePile().size(), "3P: Waste pile should be empty if player gets another turn.");
                } else {
                    // Turn advanced normally.
                    assertEquals(initialWastePileSize + cardsToPlay.size(), game.getWastePile().size(), "3P: Waste pile should increase by one if turn advances.");
                }
            } else {
                // --- Action: No playable card found ---
                if (!currentPlayer.getHand().isEmpty() || !currentPlayer.getUpcards().isEmpty()) {
                    // --- Sub-Phase: Hand or Up-cards ---
                    // Player must pick up the pile if they have no valid moves from hand or up-cards.
                    game.pickUpWastePile();
                    assertEquals(0, game.getWastePile().size(), "3P: Waste pile should be empty after pickup.");
                    assertEquals(initialHandSize + initialWastePileSize, currentPlayer.getHand().size(), "3P: Player's hand should contain the picked-up pile.");
                    assertNotEquals(currentPlayer, game.getCurrentPlayer(), "3P: Turn should advance after picking up pile.");
                } else {
                    // --- Sub-Phase: Down-cards ---
                    // Player must play a down-card blindly.
                    Card downCard = currentPlayer.getDowncards().get(0);
                    boolean playSuccessful = game.playCards(Collections.singletonList(downCard));

                    if (!playSuccessful) {
                        // --- Verification: Check penalty for invalid down-card ---
                        assertEquals(0, game.getWastePile().size(), "3P: Waste pile should be empty after penalty pickup.");
                        assertEquals(initialWastePileSize + 1, currentPlayer.getHand().size(), "3P: Player's hand should contain the pile and the invalid down card.");
                    }
                }
            }
            turn++;
        }

        // --- Final Verification ---
        assertTrue(turn < maxTurns, "Game did not finish within the turn limit (" + maxTurns + ") for 3 players.");
        assertTrue(game.isFinished(), "3-player game should be finished.");
        assertEquals(1, game.getPlayers().size(), "There should be one loser left in a 3-player game.");
    }
}
