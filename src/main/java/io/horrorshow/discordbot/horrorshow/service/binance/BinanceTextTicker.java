package io.horrorshow.discordbot.horrorshow.service.binance;

import io.horrorshow.discordbot.horrorshow.service.RespondsToDiscordMessage;
import io.horrorshow.discordbot.horrorshow.service.response.TextResponse;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BinanceTextTicker implements RespondsToDiscordMessage<TextResponse> {

    private static final Pattern CMD_AVERAGE_PRICE = Pattern.compile("^\\$avgPrice [A-Z0-9-_.]{1,20}$");
    private static final Pattern CMD_PRICE = Pattern.compile("^\\$price [A-Z0-9-_.]{1,20}$");
    private static final Pattern CMD_ALL_TOKENS = Pattern.compile("^\\$allTokens$");
    private static final Pattern CMD_ALL_PRICES = Pattern.compile("^\\$allPrices$");

    private static final Set<Pattern> MATCHES = Set.of(CMD_AVERAGE_PRICE, CMD_PRICE, CMD_ALL_TOKENS, CMD_ALL_PRICES);

    private final BinanceApiWrapper binanceApi;

    public BinanceTextTicker(@Autowired BinanceApiWrapper binanceApiWrapper) {
        this.binanceApi = binanceApiWrapper;
    }

    private String[] token(String s) {
        return s.split(" ");
    }

    private TextResponse getPriceOf(String rawMsgContent) {
        var t = token(rawMsgContent);
        if (t.length > 1)
            return new TextResponse(binanceApi.getPrice(t[1]).toString());
        else
            return new TextResponse("missing symbol parameter: $price <SYMBOL>");
    }

    private TextResponse getAvgPriceOf(String rawMsgContent) {
        var tokens = rawMsgContent.split(" ");
        if (tokens.length > 1)
            return new TextResponse(binanceApi.getAveragePrice(tokens[1]));
        else
            return new TextResponse("missing symbol parameter: $avgPrice <SYMBOL>");
    }

    private List<TextResponse> getAllTokens() {
        int max = 2000;
        var tokensString = String.join(", ", binanceApi.getAllTokens());
        return divideStringIntoParts(max, tokensString);
    }

    @NotNull
    private List<TextResponse> divideStringIntoParts(int max, String tokensString) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < tokensString.length(); i += max) {
            parts.add(tokensString.substring(i, Math.min(tokensString.length(), i + max)));
        }
        return parts.stream().map(TextResponse::new).collect(Collectors.toList());
    }

    private List<TextResponse> getAllPrices() {
        int max = 2000;
        var allPrices = binanceApi.getAllPrices()
                .stream().map(tickerPrice ->
                        MessageFormat.format("[{0}:{1}]",
                                tickerPrice.getSymbol(), tickerPrice.getPrice()))
                .collect(Collectors.joining(""));
        return divideStringIntoParts(max, allPrices);
    }

    @Override
    public boolean canCompute(String message) {
        return MATCHES.stream().anyMatch(pattern -> pattern.matcher(message).matches());
    }

    @Override
    public void computeMessage(String message, Consumer<TextResponse> consumer) {
        if (CMD_AVERAGE_PRICE.matcher(message).matches()) {
            consumer.accept(getAvgPriceOf(message));
        } else if (CMD_PRICE.matcher(message).matches()) {
            consumer.accept(getPriceOf(message));
        } else if (CMD_ALL_TOKENS.matcher(message).matches()) {
            getAllTokens().forEach(consumer);
        } else if (CMD_ALL_PRICES.matcher(message).matches()) {
            getAllPrices().forEach(consumer);
        } else {
            consumer.accept(new TextResponse("couldn't compute message %s".formatted(message)));
        }
    }

    @Override
    public String help() {
        return "Available commands for %s\n%s"
                .formatted(this.getClass().getSimpleName(),
                        MATCHES.stream().map(s -> "    " + s)
                                .collect(Collectors.joining("\n")));
    }
}
