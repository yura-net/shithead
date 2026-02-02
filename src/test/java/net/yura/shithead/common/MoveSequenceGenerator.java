package net.yura.shithead.common;

import net.yura.cardsengine.Card;
import net.yura.cardsengine.Deck;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MoveSequenceGenerator {

    public static void main(String[] args) throws Exception {
        new MoveSequenceGenerator().generateMoveSequence();
    }

    public void generateMoveSequence() throws IOException {
        CommandParser parser = new CommandParser();
        ShitheadGame game = new ShitheadGame(2);
        Deck deck = game.getDeck();
        deck.setRandom(new Random(123)); // Same seed as in the test
        game.deal();

        List<String> commands = new ArrayList<>();
        for (Player player : game.getPlayers()) {
            String command = "ready " + CommandParser.encodePlayerName(player.getName());
            parser.parse(game, command);
            commands.add(command);
        }

        while (!game.isFinished()) {
            String command = AutoPlay.getValidGameCommand(game);

            System.out.println("playing: " + command);

            commands.add(command);
            parser.parse(game, command);
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("src/test/resources/hardcoded-game-sequence.txt"))) {
            for (String command : commands) {
                writer.write(command);
                writer.newLine();
            }
        }
    }
}
