package net.yura.shithead.client;

import net.yura.cardsengine.Card;
import net.yura.mobile.gui.ActionListener;
import net.yura.mobile.gui.Application;
import net.yura.mobile.gui.components.Button;
import net.yura.mobile.gui.components.Frame;
import net.yura.mobile.gui.components.Menu;
import net.yura.mobile.gui.layout.XULLoader;
import net.yura.mobile.util.Properties;
import net.yura.shithead.common.AcesHighCardComparator;
import net.yura.shithead.common.CommandParser;
import net.yura.shithead.common.Player;
import net.yura.shithead.common.ShitheadGame;
import net.yura.shithead.uicomponents.GameView;
import net.yura.shithead.uicomponents.GameViewListener;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class GameUI implements ActionListener, GameViewListener {

    // UI components
    Properties uiTextString;
    private final GameView gameView;
    private final Button playButton;
    private final Menu menu;

    final ActionListener gameCommandListener;
    ActionListener closeActionListener;

    // game properties
    final ShitheadGame game;
    private final String playerUsername;

    public GameUI(Properties properties, ShitheadGame game, String playerUsername, ActionListener gameCommandActionListener) {
        this.game = game;
        this.playerUsername = playerUsername;
        this.gameCommandListener = gameCommandActionListener;
        this.uiTextString = properties;

        XULLoader loader = new XULLoader();
        try (InputStream stream = ShitHeadApplication.class.getResourceAsStream("/game_view.xml")) {
            loader.load(new InputStreamReader(stream), this, uiTextString);
        }
        catch (Exception ex) {
            throw new IllegalStateException(ex);
        }

        gameView = (GameView) loader.find("game_view");
        gameView.setGameCommandListener(this);
        gameView.setGame(game, playerUsername);
        gameView.setTitle(properties.getProperty("app.title"));
        playButton = (Button) loader.find("play_button");
        updateButton();

        Frame frame = (Frame)loader.getRoot();
        menu = (Menu) loader.find("menu");



        frame.setMaximum(true);
        frame.revalidate();
        frame.setVisible(true);

        wakelock(true);
    }

    private void wakelock(boolean wakelock) {
        if (Application.getPlatform() == Application.PLATFORM_ANDROID || Application.getPlatform() == Application.PLATFORM_IOS) {
            Application.openURL("wakelock://" + wakelock);
        }
    }

    public Menu getMenu() {
        return menu;
    }

    public void setTitle(String name) {
        gameView.setTitle(name);
    }

    @Override
    public void swapCards(Card card1, Card card2) {
        gameCommandListener.actionPerformed("swap " + CommandParser.encodePlayerName(playerUsername) +" " + card1 + " " + card2);
    }

    @Override
    public void playVisibleCard(boolean hand, List<Card> cards) {
        gameCommandListener.actionPerformed("play " + (hand ? "hand " : "up ") + cards.stream().map(Object::toString).collect(Collectors.joining(" ")));
        updateButton();
    }

    @Override
    public void pickUpWaste() {
        gameCommandListener.actionPerformed("pickup");
    }

    @Override
    public void playDeck() {
        gameCommandListener.actionPerformed("play deck");
    }

    @Override
    public void playDowncard() {
        gameCommandListener.actionPerformed("play down 0");
    }

    @Override
    public void actionPerformed(String actionCommand) {
        if (Frame.CMD_CLOSE.equals(actionCommand)) {
            if (closeActionListener != null) {
                closeActionListener.actionPerformed(actionCommand);
            }
            close();
        }
        else if ("play".equals(actionCommand)) {
            if (game.isRearranging()) {
                gameView.clearSelectedCards(); // just in case we left anything selected
                gameCommandListener.actionPerformed("ready " + CommandParser.encodePlayerName(playerUsername));
            }
            else {
                List<Card> cards = gameView.getSelectedCards();
                gameView.clearSelectedCards();
                playVisibleCard(!game.getPlayer(playerUsername).getHand().isEmpty(), cards);
            }
            updateButton();
        }
        else if ("sort".equals(actionCommand)) {
            Player player = game.getPlayer(playerUsername);
            if (player != null) {
                sortHand(player);
            }
            gameView.revalidate();
            gameView.repaint();
        }
        else {
            // TODO game actions!!
            System.out.println("unknown command " + actionCommand);
        }
    }

    @Override
    public void updateButton() {
        Player player = game.getPlayer(playerUsername);
        if (game.isRearranging() && player != null && !game.getPlayersReady().contains(player)) {
            playButton.setText(uiTextString.getString("game.ready"));
            playButton.setFocusable(true);
        }
        else if (!gameView.getSelectedCards().isEmpty()) {
            playButton.setText(uiTextString.getString("game.play"));
            playButton.setFocusable(true);
        }
        else {
            playButton.setText(uiTextString.getString("game.play"));
            playButton.setFocusable(false);
        }
    }

    public void close() {
        gameView.getWindow().setVisible(false);

        wakelock(false);
    }

    public void newCommand(String message) {
        new CommandParser().execute(game, message);
        updateButton();
        gameView.revalidate();
        gameView.repaint();
    }

    public static void sortHand(Player player) {
        if (player != null) {
            List<Card> hand = player.getHand();
            List<Card> sortedHand = new ArrayList<>(hand);
            sortedHand.sort(new AcesHighCardComparator());
            if (hand.equals(sortedHand)) {
                Collections.reverse(hand);
            } else {
                hand.clear();
                hand.addAll(sortedHand);
            }
        }
    }
}
