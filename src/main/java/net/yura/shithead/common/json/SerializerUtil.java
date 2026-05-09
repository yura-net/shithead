package net.yura.shithead.common.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.FromStringDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import net.yura.cardsengine.Card;
import net.yura.cardsengine.Rank;
import net.yura.cardsengine.Suit;
import net.yura.shithead.common.Player;
import net.yura.shithead.common.ShitheadGame;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SerializerUtil {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Map<Character, Rank> RANK_MAP = new HashMap<>();
    private static final Map<Character, Suit> SUIT_MAP = new HashMap<>();

    static {
        for (Rank r : Rank.THIRTEEN_RANKS) {
            RANK_MAP.put(r.toChar(), r);
        }
        for (Suit s : Suit.FOUR_SUITS) {
            SUIT_MAP.put(s.toChar(), s);
        }

        SimpleModule module = new SimpleModule();
        module.addSerializer(ShitheadGame.class, new GameSerializer());
        module.addSerializer(Player.class, new PlayerSerializer());
        module.addSerializer(Card.class, new ToStringSerializer());

        module.addDeserializer(ShitheadGame.class, new GameDeserializer());
        module.addDeserializer(Player.class, new PlayerDeserializer());
        module.addDeserializer(Card.class, new FromStringDeserializer<Card>(Card.class) {
            @Override
            protected Card _deserialize(String value, DeserializationContext ctxt) {
                return cardFromString(value);
            }
        });

        mapper.registerModule(module);
    }

    public static Card cardFromString(String s) {
        if (s == null || s.length() != 2) {
            throw new IllegalArgumentException("Card string must be non-null and 2 characters long, but was: " + s);
        }
        Rank rank = RANK_MAP.get(s.charAt(0));
        Suit suit = SUIT_MAP.get(s.charAt(1));
        if (rank == null || suit == null) {
            throw new IllegalArgumentException("Invalid rank or suit character in card string: " + s);
        }
        return Card.getCardByRankSuit(rank, suit);
    }

    /**
     * Serializes the game state to JSON, optionally from the perspective of a specific player.
     *
     * @param game       The game object to serialize.
     * @param playerName The name of the player for whom the JSON is being generated.
     *                   If null, the full game state is serialized.
     * @return A JSON string representing the game state.
     * @throws JsonProcessingException If an error occurs during serialization.
     */
    public static String toJSON(ShitheadGame game, String playerName) {
        ObjectMapper localMapper = mapper.copy();
        if (playerName != null) {
            localMapper.setConfig(localMapper.getSerializationConfig().withAttribute(PlayerSerializer.PLAYER_CONTEXT_KEY, playerName));
        }
        try {
            return localMapper.writerWithDefaultPrettyPrinter().writeValueAsString(game);
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Deserializes a ShitheadGame from a JSON string.
     *
     * @param json The JSON string representing the game state.
     * @return A new ShitheadGame object.
     * @throws IOException If an error occurs during deserialization.
     */
    public static ShitheadGame fromJSON(String json) {
        try {
            return mapper.readValue(json, ShitheadGame.class);
        }
        catch (JsonProcessingException e) {
            throw new IllegalArgumentException("bad json " + json, e);
        }
    }

    public static String optionsToJson(Map<String, String> options) {
        try {
            return mapper.writeValueAsString(options);
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Map<String, String> optionsFromJson(String json) {
        if (json == null) {
            return new HashMap<>();
        }
        try {
            return mapper.readValue(json, new TypeReference<Map<String, String>>(){});
        }
        catch (JsonProcessingException e) {
            return new HashMap<>();
        }
    }
}