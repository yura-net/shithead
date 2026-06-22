package net.yura.shithead.client;

import net.yura.cardsengine.Card;
import net.yura.cardsengine.Deck;
import net.yura.mobile.gui.Application;
import net.yura.mobile.gui.DesktopPane;
import net.yura.mobile.util.RemoteTest;
import net.yura.shithead.common.AutoPlay;
import net.yura.shithead.common.Player;
import net.yura.shithead.common.ShitheadGame;
import net.yura.shithead.uicomponents.CardImageManager;
import net.yura.shithead.uicomponents.CardLocation;
import net.yura.shithead.uicomponents.GameView;
import net.yura.shithead.uicomponents.PlayerHand;
import net.yura.shithead.uicomponents.UICard;
import org.junit.jupiter.api.Test;

import java.awt.EventQueue;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameUIPlayTest {

    private static final String MY_PLAYER = ShitHeadApplication.SINGLE_PLAYER_NAME;

    public static class TestApp extends ShitHeadApplicationTest.TestApp {

        @Override
        protected void createNewSinglePlayerGame(int numPlayers, int numDecks, boolean sevenGoLow) {
            // Use a fixed random seed so the game is deterministic regardless of which tests
            // ran before this one. Two players keeps the game short.
            Deck deck = new Deck(1);
            deck.setRandom(new Random(42L));
            ShitheadGame game = new ShitheadGame(2, deck);
            game.setSevenGoLow(sevenGoLow);
            game.deal();

            Player me = game.getPlayer(SINGLE_PLAYER_NAME);
            for (Player player : game.getPlayers()) {
                if (me != player) {
                    game.playerReady(player);
                }
            }

            openSinglePlayerGame(game);
        }

        @Override
        protected void doAITurn() {
            ShitheadGame game = singlePlayerGame;
            if (game == null) return;
            Player me = game.getPlayer(SINGLE_PLAYER_NAME);
            while (singlePlayerGame != null && !game.isFinished() && game.isPlaying() && game.getCurrentPlayer() != me) {
                parser.parse(game, AutoPlay.getValidGameCommand(game));
            }
        }
    }

    @Test
    public void playGameToEnd() throws Exception {
        HeadlessRunner.runApplication(TestApp.class);
        await().atMost(5, TimeUnit.SECONDS).until(() -> !DesktopPane.getDesktopPane().getAllFrames().isEmpty());
        EventQueue.invokeAndWait(new Thread());

        // Navigate to the game setup screen via the "Single Player" button.
        assertTrue(RemoteTest.clickText(DesktopPane.getDesktopPane().getSelectedFrame(), "Single Player"));

        // Wait for the game setup dialog (a second frame) to appear.
        await().atMost(5, TimeUnit.SECONDS).until(() -> DesktopPane.getDesktopPane().getAllFrames().size() >= 2);
        EventQueue.invokeAndWait(new Thread());

        // Start the game by clicking "Create".
        assertTrue(RemoteTest.clickText(DesktopPane.getDesktopPane().getSelectedFrame(), "Create"));
        EventQueue.invokeAndWait(new Thread());

        ShitHeadApplication app = (ShitHeadApplication) Application.getInstance();
        await().atMost(5, TimeUnit.SECONDS).until(() -> app.singlePlayerGame != null);

        ShitheadGame game = app.singlePlayerGame;
        GameView gameView = app.singlePlayerGameUI.gameView;
        gameView.doLayout();

        // Click "Ready" to end the card-rearranging phase for our player.
        if (game.isRearranging()) {
            assertTrue(RemoteTest.clickText(gameView, "Ready"));
            EventQueue.invokeAndWait(new Thread());
        }

        int maxTurns = 2000;
        while (!game.isFinished() && maxTurns-- > 0) {
            if (!game.isPlaying()) break;
            Player current = game.getCurrentPlayer();
            if (current == null) break;

            if (MY_PLAYER.equals(current.getName())) {
                gameView.doLayout();
                settleAnimations(gameView, game);
                playTurnViaUI(game, gameView);
                EventQueue.invokeAndWait(new Thread());
            }
        }

        assertTrue(game.isFinished(), "Game should have finished within the turn limit");
    }

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

    private void playTurnViaUI(ShitheadGame game, GameView gameView) {
        Player player = game.getPlayer(MY_PLAYER);
        PlayerHand hand = gameView.getPlayerHand(MY_PLAYER);

        if (player.getHand().isEmpty() && player.getUpcards().isEmpty()) {
            List<UICard> downcards = hand.getUiCards(CardLocation.DOWN_CARDS);
            if (!downcards.isEmpty()) {
                clickCard(gameView, downcards.get(0));
            }
            return;
        }

        List<Card> best = AutoPlay.findBestVisibleCards(game);

        if (!best.isEmpty()) {
            for (UICard uiCard : hand.getUiCards()) {
                if (best.get(0).equals(uiCard.getCard())) {
                    clickCard(gameView, uiCard);
                    // Clicking may select rather than immediately play; submit if so.
                    if (!gameView.getSelectedCards().isEmpty()) {
                        RemoteTest.clickText(gameView, "Play");
                    }
                    return;
                }
            }
        } else if (!game.getDeck().getCards().isEmpty()) {
            List<UICard> deckCards = gameView.getDeckAndWasteCards().stream()
                    .filter(c -> c.getLocation() == CardLocation.DECK)
                    .collect(Collectors.toList());
            if (!deckCards.isEmpty()) {
                clickCard(gameView, deckCards.get(0));
            }
        } else {
            List<UICard> wasteCards = gameView.getDeckAndWasteCards().stream()
                    .filter(c -> c.getLocation() == CardLocation.WASTE)
                    .collect(Collectors.toList());
            if (!wasteCards.isEmpty()) {
                clickCard(gameView, wasteCards.get(wasteCards.size() - 1));
            }
        }
    }

    private void clickCard(GameView gameView, UICard uiCard) {
        int cx = uiCard.getX() + CardImageManager.cardWidth / 2;
        // Click near the top of the card rather than the center. In the multi-row hand
        // layout, rows are spaced cardHeight/2 apart and vertically overlap. Clicking at
        // the center hits the row below (which has a higher list index and thus wins
        // the reverse-order hit-test). Clicking near the top stays in the unique upper
        // portion of this card's row, so the correct card is targeted.
        int cy = uiCard.getY() + 1;
        gameView.processMouseEvent(DesktopPane.RELEASED, cx, cy, null);
    }
}
