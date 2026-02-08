package net.yura.shithead.client;

import net.yura.cardsengine.Card;
import net.yura.cardsengine.Rank;
import net.yura.cardsengine.Suit;
import net.yura.lobby.mini.MiniLobbyClient;
import net.yura.lobby.mini.MiniLobbyGame;
import net.yura.lobby.model.Game;
import net.yura.lobby.model.GameType;
import net.yura.lobby.model.Player;
import net.yura.mobile.gui.Application;
import net.yura.mobile.gui.Icon;
import net.yura.mobile.gui.components.Button;
import net.yura.mobile.gui.components.ComboBox;
import net.yura.mobile.gui.components.TextComponent;
import net.yura.mobile.util.Option;
import net.yura.mobile.util.Properties;
import net.yura.mobile.gui.ActionListener;
import net.yura.mobile.gui.components.Frame;
import net.yura.mobile.gui.components.Spinner;
import net.yura.mobile.gui.components.TextField;
import net.yura.mobile.gui.layout.XULLoader;
import net.yura.shithead.common.ShitheadGame;
import net.yura.shithead.common.json.SerializerUtil;
import net.yura.shithead.uicomponents.CardImageManager;
import net.yura.shithead.uicomponents.Icons;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.jar.Manifest;

public class MiniLobbyShithead implements MiniLobbyGame {

    Properties strings;

    protected MiniLobbyClient lobby;
    private GameUI openGameUI;
    private Button resignButton;

    public MiniLobbyShithead(Properties strings) {
        this.strings = strings;
    }

    @Override
    public void addLobbyGameMoveListener(MiniLobbyClient lgl) {
        lobby = lgl;

        lobby.getChatButton().setVisible(false);
    }

    @Override
    public void openChat() {
        // TODO
    }

    @Override
    public boolean isMyGameType(GameType gametype) {
        return "Shithead".equals(gametype.getName());
    }

    /**
     * proportions should be 75 x 47 (not too far from 5:3 aspect ratio)
     * @see net.yura.lobby.mini.GameRenderer
     */
    @Override
    public Icon getIconForGame(Game game) {
        Icon qos = CardImageManager.getCardImage(Card.getCardByRankSuit(Rank.QUEEN, Suit.SPADES));
        Icon ace = CardImageManager.getCardImage(Card.getCardByRankSuit(Rank.ACE, Suit.SPADES));
        int num = game.getMaxPlayers();
        if (num == 0) { num = Rank.KING.toInt(); } // if we have no players, then just show king
        Icon countIcon = CardImageManager.getCardImage(Card.getCardByRankSuit(Rank.THIRTEEN_RANKS[num - 1], Suit.HEARTS));
        return new Icons(new Icon[] {qos, countIcon, ace});
    }

    @Override
    public String getGameDescription(Game game) {
        return "";
    }

    @Override
    public void openGameSetup(final GameType gameType) {

        XULLoader gameSetupLoader = ShitHeadApplication.createNewGameScreen(strings, loader -> {
            Spinner players = (Spinner)loader.find("players");
            int numPlayers = (Integer)players.getValue();
            TextField gamename = (TextField)loader.find("gamename");
            String gameName = gamename.getText();
            int timeout = Integer.parseInt(((Option) ((ComboBox) loader.find("TimeoutValue")).getSelectedItem()).getKey());

            // TODO for now options cant be null, but in next version of lobby it can
            Game newGame = new Game(gameName, "blank", numPlayers, timeout);

            if (((Button) loader.find("private")).isSelected()) {
                newGame.setMagicWord(((TextComponent) loader.find("password")).getText());
            }

            newGame.setType(gameType);
            lobby.createNewGame(newGame);
        });

        TextField gamename = (TextField)gameSetupLoader.find("gamename");
        gamename.setText(lobby.whoAmI() + "'s game");

        Frame dialog = (Frame)gameSetupLoader.getRoot();
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    @Override
    public void prepareAndOpenGame(Game game) {
        lobby.mycom.playGame(game.getId());
    }

    // not used, only used when byte[] are read with java serialisation into an object
    @Override
    public void objectForGame(Object object) { }

    @Override
    public void stringForGame(String message) {
        if (openGameUI == null) {
            ShitheadGame onlineGame = SerializerUtil.fromJSON(message);
            openGameUI = new GameUI(strings, onlineGame, lobby.whoAmI(), new ActionListener() {
                @Override
                public void actionPerformed(String gameAction) {
                    lobby.sendGameMessage(gameAction);
                }
            });
            openGameUI.setTitle(lobby.getCurrentOpenGame().getName());
            ActionListener actionListener = new ActionListener() {
                @Override
                public void actionPerformed(String actionCommand) {
                    if (Frame.CMD_CLOSE.equals(actionCommand)) {
                        lobby.closeGame();
                        openGameUI = null;
                        resignButton = null;
                    }
                    else if ("resign".equals(actionCommand)) {
                        lobby.resign();
                    }
                    else if ("chat".equals(actionCommand)) {
                        lobby.sendChatMessage();
                    }
                    else {
                        System.err.println("unknown command: " +actionCommand);
                    }
                }
            };

            openGameUI.closeActionListener = actionListener;
            openGameUI.getMenu().setVisible(true);

            Button chatButton = new Button(strings.getProperty("lobby.room.chat"));
            chatButton.setActionCommand("chat");
            chatButton.addActionListener(actionListener);
            openGameUI.getMenu().add(chatButton);

            if (lobby.getCurrentOpenGame().hasPlayer(lobby.whoAmI())) {
                resignButton = new Button(strings.getProperty("game.resign"));
                resignButton.setActionCommand("resign");
                resignButton.addActionListener(actionListener);
                openGameUI.getMenu().add(resignButton);
            }
        }
        else {
            openGameUI.newCommand(message);

            // check we are still in the game
            if (resignButton != null && openGameUI.game.getPlayers().stream().noneMatch(p -> p.getName().equals(lobby.whoAmI()))) {
                openGameUI.getMenu().remove(resignButton);
                resignButton = null;
            }
        }
    }

    @Override
    public void connected(String username) {
        // dont need to do anything
    }

    @Override
    public void disconnected() {
        openGameUI.close();
        openGameUI = null;
        resignButton = null;
    }

    @Override
    public void loginGoogle() { }

    @Override
    public void gameStarted(int id) { }

    @Override
    public String getAppName() {
        String appNamePrefix;
        if (Application.getPlatform() == Application.PLATFORM_ANDROID) {
            appNamePrefix = "Android";
        }
        else if (Application.getPlatform() == Application.PLATFORM_IOS) {
            appNamePrefix = "iOS";
        }
        else {
            appNamePrefix = "Desktop";
        }
        return appNamePrefix + "Shithead";
    }

    @Override
    public String getAppVersion() {
        String versionName = System.getProperty("versionName");
        if (versionName == null) {
            try (InputStream stream = Application.getResourceAsStream("/META-INF/MANIFEST.MF")) {
                versionName = new Manifest(stream).getMainAttributes().getValue("versionName");
            }
            catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return versionName;
    }

    @Override
    public void lobbyShutdown() {

    }

    @Override
    public void showMessage(String fromwho, String message) {
        MiniLobbyClient.toast(fromwho != null ? fromwho + ": " + message : message);
    }


    @Override
    public void playerRenamed(String oldName, String newName) {

    }

    @Override
    public void playerAdded(String name) {

    }

    @Override
    public void playerRemoved(String name) {

    }

    @Override
    public void addSpectator(Player player) { }
    @Override
    public void removeSpectator(String player) { }
    @Override
    public void renameSpectator(String oldname, String newname, int newtype) { }
    @Override
    public void updatePlayerList(Collection<Player> playersInGame, String whoTurn) { }
    @Override
    public void gameActionPerformed(int state) { }
}
