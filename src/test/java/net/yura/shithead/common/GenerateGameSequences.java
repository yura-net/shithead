package net.yura.shithead.common;

import net.yura.cardsengine.Deck;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GenerateGameSequences {

    @Test
    public void generateHardcoded2PlayerSequence() throws Exception {
        CommandParser parser = new CommandParser();
        ShitheadGame game = new ShitheadGame(2);
        game.setGoLowRuleEnabled(true);
        game.getDeck().setRandom(new Random(123));
        game.deal();
        List<String> commands = new ArrayList<>();
        int turns = 0;
        while (!game.isFinished() && turns++ < 500) {
            String command = AutoPlay.getValidGameCommand(game, game.isRearranging()
                ? game.getPlayers().stream().filter(p -> !game.getPlayersReady().contains(p)).findFirst().get().getName()
                : game.getCurrentPlayer().getName());
            parser.execute(game, CommandParser.getMutationCommand(game, command));
            commands.add(command);
        }
        System.out.println("finished=" + game.isFinished());
        if (game.isFinished() && game.getPlayers().size() == 1) {
            var l = game.getPlayers().get(0);
            System.out.println("2player loser hand=" + l.getHand());
            System.out.println("2player loser upcards=" + l.getUpcards());
            System.out.println("2player loser downcards=" + l.getDowncards());
        }
        Files.write(Paths.get("src/test/resources/hardcoded-game-sequence.txt"), commands);
    }

    @Test
    public void generate2DecksSequence() throws Exception {
        CommandParser parser = new CommandParser();
        ShitheadGame game = new ShitheadGame(6, new Deck(2));
        game.setGoLowRuleEnabled(true);
        game.getDeck().setRandom(new Random(123));
        game.deal();
        List<String> commands = new ArrayList<>();
        int turns = 0;
        while (!game.isFinished() && turns++ < 2000) {
            String command = AutoPlay.getValidGameCommand(game, game.isRearranging()
                ? game.getPlayers().stream().filter(p -> !game.getPlayersReady().contains(p)).findFirst().get().getName()
                : game.getCurrentPlayer().getName());
            parser.execute(game, CommandParser.getMutationCommand(game, command));
            commands.add(command);
        }
        System.out.println("finished=" + game.isFinished());
        if (game.isFinished() && game.getPlayers().size() == 1) {
            var l = game.getPlayers().get(0);
            System.out.println("2decks loser hand=" + l.getHand());
            System.out.println("2decks loser upcards=" + l.getUpcards());
            System.out.println("2decks loser downcards=" + l.getDowncards());
        }
        Files.write(Paths.get("src/test/resources/2decks-game-sequence.txt"), commands);
    }
}
