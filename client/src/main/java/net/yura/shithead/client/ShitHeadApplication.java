package net.yura.shithead.client;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ResourceBundle;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import net.yura.lobby.mini.GameRenderer;
import net.yura.lobby.mini.MiniLobbyClient;
import net.yura.mobile.gui.ActionListener;
import net.yura.mobile.gui.Animation;
import net.yura.mobile.gui.Application;
import net.yura.mobile.gui.DesktopPane;
import net.yura.mobile.gui.Icon;
import net.yura.mobile.gui.border.BackgroundBorder;
import net.yura.mobile.gui.border.Border;
import net.yura.mobile.gui.border.EdgeToEdgeBorder;
import net.yura.mobile.gui.components.Button;
import net.yura.mobile.gui.components.Frame;
import net.yura.mobile.gui.components.OptionPane;
import net.yura.mobile.gui.components.Spinner;
import net.yura.mobile.gui.components.TextComponent;
import net.yura.mobile.gui.components.Window;
import net.yura.mobile.gui.layout.XULLoader;
import net.yura.mobile.util.Properties;
import net.yura.shithead.common.AutoPlay;
import net.yura.shithead.common.CommandParser;
import net.yura.shithead.common.Player;
import net.yura.shithead.common.ShitheadGame;
import net.yura.shithead.common.json.SerializerUtil;
import javax.microedition.lcdui.Image;

public class ShitHeadApplication extends Application implements ActionListener {

    private static final String SINGLE_PLAYER_NAME = "Player 1";

    static final Border background;

    static {
        try {
            background = new EdgeToEdgeBorder(new BackgroundBorder(Image.createImage(ShitHeadApplication.class.getResourceAsStream("/table.jpg"))));
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Properties properties;

    private ShitheadGame singlePlayerGame;

    private MiniLobbyClient minilobby;

    protected void initialize(DesktopPane dp) {

        // this needs to be set right at the start BEFORE the animation thread starts
        // as soon as the animation thread starts, changes to this will not take affect
        Animation.FPS = 30;

        setupTheme(dp);
        MiniLobbyClient.loadThemeExtension();

        ResourceBundle bundle = ResourceBundle.getBundle("game_text");
        properties = new Properties() {
            @Override
            public String getProperty(String key) {
                return bundle.getString(key);
            }
        };

        openMainMenu();

        loadState();
    }

    /**
     * this method is needed so UI tests can setup any extra theme properties they need
     */
    protected void setupTheme(DesktopPane dp) {
        dp.setLookAndFeel(DesktopPane.getSystemLookAndFeelClassName());
    }

    private void openMainMenu() {
        System.out.println("OPENING MENU");

        XULLoader loader = new XULLoader();
        try (InputStream stream = ShitHeadApplication.class.getResourceAsStream("/main_menu.xml")) {
            loader.load(new InputStreamReader(stream), this, properties);

            // setup banner image
            Button banner = (Button) loader.find("banner");
            banner.setMargin(0);
            int width = Math.min(DesktopPane.getDesktopPane().getWidth(), DesktopPane.getDesktopPane().getHeight());
            Icon img = new Icon(Image.createImage(ShitHeadApplication.class.getResourceAsStream("/banner.jpg")));
            GameRenderer.ScaledIcon icon = new GameRenderer.ScaledIcon(width, (int) ((width / (double)img.getIconWidth()) * img.getIconHeight()));
            icon.setIcon(img);
            banner.setIcon(icon);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

        Frame frame = (Frame)loader.getRoot();

        frame.setBackground(0x00FFFFFF); // force override
        frame.setBorder(background);

        frame.setMaximum(true);
        frame.setVisible(true);
    }

    public void actionPerformed(String actionCommand) {
        if ("multiplayer".equals(actionCommand)) {
            if (minilobby == null) {
                minilobby = new MiniLobbyClient(new MiniLobbyShithead(properties));
                minilobby.addCloseListener(this);
            }
            minilobby.connect(MiniLobbyClient.LOBBY_SERVER);

            // TODO to avoid warning in logs we should remove minilobby from any current parent

            Frame frame = (Frame)DesktopPane.getDesktopPane().getSelectedFrame();
            frame.setContentPane(minilobby.getRoot());
            frame.revalidate();
            frame.repaint();
        }
        else if ("singleplayer".equals(actionCommand)) {

            XULLoader gameSetupLoader = createNewGameScreen(properties, loader -> {
                Spinner players = (Spinner)loader.find("players");
                int numPlayers = (Integer)players.getValue();
                createNewGame(numPlayers);
            });

            gameSetupLoader.find("gamenameLabel").setVisible(false);
            gameSetupLoader.find("gamename").setVisible(false);
            gameSetupLoader.find("timeoutLabel").setVisible(false);
            gameSetupLoader.find("TimeoutValue").setVisible(false);
            gameSetupLoader.find("private").setVisible(false);
            gameSetupLoader.find("password").setVisible(false);

            Frame dialog = (Frame)gameSetupLoader.getRoot();
            dialog.pack();
            dialog.setLocationRelativeTo(null);
            dialog.setVisible(true);
        }
        else if ("about".equals(actionCommand)) {
            String versionName = System.getProperty("versionName");
            String versionCode = System.getProperty("versionCode");

            if (versionName == null) {
                try (InputStream stream = Application.getResourceAsStream("/META-INF/MANIFEST.MF")) {
                    Manifest manifest = new Manifest(stream);
                    Attributes attributes = manifest.getMainAttributes();
                    versionName = attributes.getValue("versionName");
                    versionCode = attributes.getValue("versionCode");
                }
                catch (IOException ex) {
                    ex.printStackTrace();
                }
            }

            // TODO move about text to properties file
            OptionPane.showMessageDialog(null, new String[] {
                    "Version: " + versionName + " (Build: " + versionCode+")",
                    "\u00a9 Copyright 2026 yura.net \ud83c\uddfa\ud83c\udde6",
                    "https://github.com/yura-net/shithead",
                    "license: GNU General Public License 3.0"}, properties.getProperty("about.title"), OptionPane.INFORMATION_MESSAGE);
        }
        else if ("banner".equals(actionCommand)) {

            Application.openURL("https://silverstreetgames.co.uk");

        }
        else if (Frame.CMD_CLOSE.equals(actionCommand)) { // close the lobby
            Window frame = minilobby.getRoot().getWindow();
            frame.setVisible(false);
            openMainMenu();
        }
        else {
            System.out.println("unknown command: " + actionCommand);
        }
    }

    private void createNewGame(int numPlayers) {

        ShitheadGame singlePlayerGame = new ShitheadGame(numPlayers);
        singlePlayerGame.deal();

        // TODO maybe we want to use AutoPlay for this also?
        Player me = singlePlayerGame.getPlayer(SINGLE_PLAYER_NAME);
        for (Player player : singlePlayerGame.getPlayers()) {
            if (me != player) {
                singlePlayerGame.playerReady(player);
            }
        }

        openGame(singlePlayerGame);
    }

    private void openGame(ShitheadGame game) {
        this.singlePlayerGame = game;
        Player me = game.getPlayer(SINGLE_PLAYER_NAME);

        final GameUI gameUI = new GameUI(properties, singlePlayerGame, SINGLE_PLAYER_NAME, new ActionListener() {
            @Override
            public void actionPerformed(String actionCommand) {
                CommandParser parser = new CommandParser();
                parser.parse(singlePlayerGame, actionCommand);

                // TODO ideally we would call GameView.layoutCards() directly as when we use revalidate it may or not get called

                DesktopPane.getDesktopPane().getSelectedFrame().revalidate();
                DesktopPane.getDesktopPane().getSelectedFrame().repaint();

                new Thread() {
                    @Override
                    public void run() {
                        ShitheadGame game = singlePlayerGame;
                        while (singlePlayerGame != null && !game.isFinished() && game.isPlaying() && game.getCurrentPlayer() != me) {
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                                break;
                            }
                            parser.parse(game, AutoPlay.getValidGameCommand(game));
                            DesktopPane.getDesktopPane().getSelectedFrame().revalidate();
                            DesktopPane.getDesktopPane().getSelectedFrame().repaint();
                        }
                    }
                }.start();
            }
        });
        gameUI.closeActionListener = new ActionListener() {
            @Override
            public void actionPerformed(String actionCommand) {
                singlePlayerGame = null;
            }
        };
    }


    public static XULLoader createNewGameScreen(Properties properties, Consumer<XULLoader> actionListener) {
        final XULLoader gameSetupLoader = new XULLoader();
        try (InputStreamReader reader = new InputStreamReader(ShitHeadApplication.class.getResourceAsStream("/game_setup.xml"))) {
            gameSetupLoader.load(reader, actionCommand -> {
                if ("create".equals(actionCommand)) {
                    actionListener.accept(gameSetupLoader);
                }
                else if ("private".equals(actionCommand)) {
                    boolean pvt = ((Button)gameSetupLoader.find("private")).isSelected();
                    gameSetupLoader.find("password").setFocusable(pvt);
                    if (pvt) {
                        gameSetupLoader.find("password").requestFocusInWindow();
                    }
                }

                if ("create".equals(actionCommand) || "back".equals(actionCommand)) {
                    ((Frame) gameSetupLoader.getRoot()).setVisible(false);
                }
            }, properties);

            ((TextComponent)gameSetupLoader.find("password")).setTitle(properties.getProperty("newgame.password"));
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return gameSetupLoader;
    }

    public void saveState() {
        File autoSave = new File(getGameDir(), "auto.save");
        if (singlePlayerGame != null && singlePlayerGame.getPlayer(SINGLE_PLAYER_NAME) != null) {
            String json = SerializerUtil.toJSON(singlePlayerGame, null);
            try (FileWriter out = new FileWriter(autoSave)) { // TODO should use StandardCharsets.UTF_8
                out.write(json);
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        else {
            // if we do not have a game open, make sure this file does not exist
            autoSave.delete();
        }
    }

    private void loadState() {
        try {
            File autoSave = new File(getGameDir(), "auto.save");
            if (autoSave.exists()) {
                String json = new Scanner(autoSave, "UTF-8").useDelimiter("\\A").next();
                autoSave.delete();
                openGame(SerializerUtil.fromJSON(json));
            }
        }
        catch (Exception ex) {
            // failed to load state, just give up
            ex.printStackTrace();
        }
    }

    private static File getGameDir() {
        File userHome = new File( System.getProperty("user.home") );
        File gameDir = new File(userHome, ".shithead");
        if (!gameDir.isDirectory() && !gameDir.mkdirs()) {
            throw new RuntimeException("can not create dir " + gameDir);
        }
        return gameDir;
    }
}
