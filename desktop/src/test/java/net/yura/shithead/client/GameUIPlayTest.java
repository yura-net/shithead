package net.yura.shithead.client;

import net.yura.cardsengine.Card;
import net.yura.mobile.gui.DesktopPane;
import net.yura.mobile.util.Properties;
import net.yura.shithead.common.AutoPlay;
import net.yura.shithead.common.CommandParser;
import net.yura.shithead.common.Player;
import net.yura.shithead.common.ShitheadGame;
import net.yura.shithead.uicomponents.CardImageManager;
import net.yura.shithead.uicomponents.CardLocation;
import net.yura.shithead.uicomponents.GameView;
import net.yura.shithead.uicomponents.PlayerHand;
import net.yura.shithead.uicomponents.UICard;
import org.junit.jupiter.api.Test;

import java.awt.EventQueue;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameUIPlayTest {

    private static final String MY_PLAYER = "Player 1";

    @Test
    public void playGameToEnd() throws Exception {
        // Boot the SwingME framework so fonts, images, and DesktopPane are ready.
        HeadlessRunner.runApplication(ShitHeadApplicationTest.TestApp.class);
        await().atMost(5, TimeUnit.SECONDS).until(() -> !DesktopPane.getDesktopPane().getAllFrames().isEmpty());
        EventQueue.invokeAndWait(new Thread());

        // Create a 2-player game directly (fewer players = faster game).
        ShitheadGame game = new ShitheadGame(Arrays.asList(MY_PLAYER, "AI"));
        game.deal();

        // Skip the card-rearranging phase: mark both players as ready immediately.
        game.playerReady(game.getPlayer("AI"));
        game.playerReady(game.getPlayer(MY_PLAYER)); // triggers game start once everyone is ready

        Properties properties = new Properties() {
            @Override
            public String getProperty(String key) {
                return key; // return key as value; sufficient for headless testing
            }
        };

        // Use a one-element array so the command-listener lambda can reference gameUI.
        GameUI[] ref = new GameUI[1];
        CommandParser parser = new CommandParser();

        ref[0] = new GameUI(properties, game, command -> {
            parser.parse(game, command);
            ref[0].gameView.doLayout(); // force immediate layout so isPlayable/isWaiting are current
        });
        ref[0].setMyUsername(MY_PLAYER);

        GameUI gameUI = ref[0];
        GameView gameView = gameUI.gameView;

        // Allow the initial layout pass to complete.
        gameView.doLayout();
        EventQueue.invokeAndWait(new Thread());

        int maxTurns = 500; // safety guard against infinite loops
        while (!game.isFinished() && maxTurns-- > 0) {
            if (!game.isPlaying()) break;

            Player current = game.getCurrentPlayer();
            if (current == null) break;

            if (MY_PLAYER.equals(current.getName())) {
                // Force layout so isWaitingForInput and isPlayable flags are current.
                gameView.doLayout();
                // Advance card animations so UICard.getX/getY reflect laid-out positions.
                settleAnimations(gameView, game);
                playTurnViaUI(game, gameView, gameUI);
            } else {
                // AI turn: compute and apply best move directly via the parser.
                parser.parse(game, AutoPlay.getValidGameCommand(game));
                gameView.doLayout();
            }

            EventQueue.invokeAndWait(new Thread());
        }

        assertTrue(game.isFinished(), "Game should have finished within the turn limit");
    }

    /**
     * Drives UICard animations to completion so that UICard.getX()/getY() match the
     * laid-out positions and hit-testing via contains() works correctly.
     */
    private void settleAnimations(GameView gameView, ShitheadGame game) {
        for (int step = 0; step < 200; step++) {
            boolean moving = false;
            for (UICard c : gameView.getDeckAndWasteCards()) {
                if (c.animate()) moving = true;
            }
            for (Player p : game.getPlayers()) {
                PlayerHand hand = gameView.getPlayerHand(p.getName());
                if (hand != null) {
                    for (UICard c : hand.getUiCards()) {
                        if (c.animate()) moving = true;
                    }
                }
            }
            if (!moving) break;
        }
    }

    /**
     * Simulates the human player's turn by clicking on an appropriate card in the GameView.
     */
    private void playTurnViaUI(ShitheadGame game, GameView gameView, GameUI gameUI) {
        Player player = game.getPlayer(MY_PLAYER);
        PlayerHand hand = gameView.getPlayerHand(MY_PLAYER);

        if (player.getHand().isEmpty() && player.getUpcards().isEmpty()) {
            // Only face-down cards remain; click the first one.
            List<UICard> downcards = hand.getUiCards(CardLocation.DOWN_CARDS);
            if (!downcards.isEmpty()) {
                clickCard(gameView, downcards.get(0));
            }
            return;
        }

        List<Card> best = AutoPlay.findBestVisibleCards(game);

        if (!best.isEmpty()) {
            // Find the UICard for the best playable card and click it.
            for (UICard uiCard : hand.getUiCards()) {
                if (best.get(0).equals(uiCard.getCard())) {
                    clickCard(gameView, uiCard);
                    // When there are multiple cards of the same rank, clicking selects
                    // rather than plays immediately.  Fire the play button to submit.
                    if (!gameView.getSelectedCards().isEmpty()) {
                        gameUI.actionPerformed("play");
                    }
                    return;
                }
            }
        } else if (!game.getDeck().getCards().isEmpty()) {
            // No visible card is playable; play blind from the deck.
            List<UICard> deckCards = gameView.getDeckAndWasteCards().stream()
                    .filter(c -> c.getLocation() == CardLocation.DECK)
                    .collect(Collectors.toList());
            if (!deckCards.isEmpty()) {
                clickCard(gameView, deckCards.get(0));
            }
        } else {
            // Deck is also empty; must pick up the waste pile.
            List<UICard> wasteCards = gameView.getDeckAndWasteCards().stream()
                    .filter(c -> c.getLocation() == CardLocation.WASTE)
                    .collect(Collectors.toList());
            if (!wasteCards.isEmpty()) {
                // Click the topmost waste card (last in list).
                clickCard(gameView, wasteCards.get(wasteCards.size() - 1));
            }
        }
    }

    /** Fires a RELEASED mouse event at the centre of the given UICard. */
    private void clickCard(GameView gameView, UICard uiCard) {
        int cx = uiCard.getX() + CardImageManager.cardWidth / 2;
        int cy = uiCard.getY() + CardImageManager.cardHeight / 2;
        gameView.processMouseEvent(DesktopPane.RELEASED, cx, cy, null);
    }
}
