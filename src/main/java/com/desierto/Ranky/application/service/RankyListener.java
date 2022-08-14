package com.desierto.Ranky.application.service;

import com.desierto.Ranky.application.service.dto.RankingConfigurationWithMessageId;
import com.desierto.Ranky.domain.entity.Account;
import com.desierto.Ranky.domain.exception.ConfigChannelNotFoundException;
import com.desierto.Ranky.domain.exception.RankingAlreadyExistsException;
import com.desierto.Ranky.domain.exception.account.AccountNotFoundException;
import com.desierto.Ranky.domain.exception.ranking.RankingNotFoundException;
import com.desierto.Ranky.domain.repository.RiotAccountRepository;
import com.desierto.Ranky.domain.valueobject.RankingConfiguration;
import com.desierto.Ranky.infrastructure.Ranky;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.beans.factory.annotation.Autowired;

@AllArgsConstructor
public class RankyListener extends ListenerAdapter {

  public static final String CREATE_COMMAND = "/create";
  public static final String DEADLINE_COMMAND = "/setDeadline";
  public static final String ADD_ACCOUNT_COMMAND = "/addAccount";
  public static final String ADD_MULTIPLE_COMMAND = "/addMultiple";
  public static final String REMOVE_ACCOUNT_COMMAND = "/removeAccount";
  public static final String RANKING_COMMAND = "/ranking";
  public static final String PRIVATE_CONFIG_CHANNEL = "desarrollo-ranky";
  public static final int RANKING_LIMIT = 100;

  @Autowired
  private RiotAccountRepository riotAccountRepository;

  @Override
  @SneakyThrows
  public void onMessageReceived(MessageReceivedEvent event) {
    Gson gson = new Gson();
    JDA bot = event.getJDA();
    if (event.getMessage().getContentRaw().startsWith(Ranky.prefix)) {
      String command = event.getMessage().getContentRaw();
      if (command.contains(CREATE_COMMAND)) {
        createRanking(event, gson, bot, command);
      }

      if (command.contains(DEADLINE_COMMAND)) {

      }
      if (command.contains(ADD_ACCOUNT_COMMAND)) {
        addAccount(event, gson, bot, command);
      }

      if (command.contains(ADD_MULTIPLE_COMMAND)) {
        addAccounts(event, gson, bot, command);
      }

      if (command.contains(REMOVE_ACCOUNT_COMMAND)) {

      }
      if (command.contains(RANKING_COMMAND)) {
        queryRanking(event, gson, bot, command);
      }


    }
  }

  private void createRanking(MessageReceivedEvent event, Gson gson, JDA bot, String command) {
    TextChannel channel = null;
    try {
      channel = getConfigChannel(bot);
    } catch (ConfigChannelNotFoundException e) {
      rethrowExceptionAfterNoticingTheServer(event, e);
    }
    String rankingName = getRankingName(command);
    try {
      if (channel != null && rankingExists(channel, rankingName)) {
        throw new RankingAlreadyExistsException();
      }
    } catch (RankingAlreadyExistsException e) {
      rethrowExceptionAfterNoticingTheServer(event, e);
    }
    if (channel != null) {
      String json = gson
          .toJson(new RankingConfiguration(rankingName));
      channel.sendMessage(json).queue();
    }
  }

  private void queryRanking(MessageReceivedEvent event, Gson gson, JDA bot, String command) {
    String rankingName = getRankingName(command);
    TextChannel configChannel = getConfigChannel(bot);
    if (rankingExists(configChannel, rankingName)) {
      RankingConfiguration rankingConfiguration = getRanking(configChannel, rankingName);
      List<Account> accounts = rankingConfiguration.getAccounts().stream()
          .map(s -> riotAccountRepository.getAccount(s)
              .orElseThrow(() -> new AccountNotFoundException(s))).sorted().collect(
              Collectors.toList());
      EmbedBuilder ranking = new EmbedBuilder();
      ranking.setTitle("\uD83D\uDC51 RANKING " + rankingName.toUpperCase() + " \uD83D\uDC51");
      String accountsToText = accounts.stream().map(Account::getFormattedForRanking).collect(
          Collectors.joining("\n"));
      ranking.setDescription(accountsToText);
      ranking.addField("Creator", "Maiky", false);
      ranking.setColor(0x000000);
      event.getChannel().sendTyping().queue();
      event.getChannel().sendMessageEmbeds(ranking.build()).queue();
      ranking.clear();
    }
  }

  private void addAccount(MessageReceivedEvent event, Gson gson, JDA bot, String command) {
    String rankingName = getRankingName(command);
    String accountToAdd = getAccountToAdd(command);
    TextChannel configChannel = getConfigChannel(bot);
    if (rankingExists(configChannel, rankingName)) {
      RankingConfigurationWithMessageId ranking = getRankingWithMessageId(configChannel,
          rankingName);
      ranking.addAccount(accountToAdd);
      event.getChannel().sendTyping().queue();
      configChannel
          .editMessageById(ranking.getMessageId(), gson.toJson(ranking.getRankingConfiguration()))
          .queue();
      event.getChannel().sendMessage("Account successfully added to the ranking").queue();
    }
  }

  private void addAccounts(MessageReceivedEvent event, Gson gson, JDA bot, String command) {
    String rankingName = getRankingName(command);
    List<String> accountsToAdd = getAccountsToAdd(command, rankingName);
    TextChannel configChannel = getConfigChannel(bot);
    if (rankingExists(configChannel, rankingName)) {
      RankingConfigurationWithMessageId ranking = getRankingWithMessageId(configChannel,
          rankingName);
      ranking.addAccounts(accountsToAdd);
      event.getChannel().sendTyping().queue();
      configChannel
          .editMessageById(ranking.getMessageId(), gson.toJson(ranking.getRankingConfiguration()))
          .queue();
      event.getChannel().sendMessage("Account successfully added to the ranking").queue();
    }
  }

  private void rethrowExceptionAfterNoticingTheServer(MessageReceivedEvent event,
      RuntimeException e) throws ConfigChannelNotFoundException, RankingAlreadyExistsException {
    event.getChannel().sendMessage(e.getMessage()).queue();
    throw e;
  }

  private boolean rankingExists(TextChannel channel, String rankingName) {
    return channel.getHistory().retrievePast(RANKING_LIMIT).complete().stream()
        .anyMatch(message -> {
          Optional<RankingConfiguration> optionalRanking = RankingConfiguration
              .fromMessageIfPossible(message);
          if (optionalRanking.isPresent()) {
            return optionalRanking.get().getName()
                .equalsIgnoreCase(rankingName);
          }
          return false;
        });
  }

  private RankingConfiguration getRanking(TextChannel channel, String rankingName) {
    return RankingConfiguration
        .fromMessage(channel.getHistory().retrievePast(RANKING_LIMIT).complete().stream()
            .filter(message -> {
              Optional<RankingConfiguration> optionalRanking = RankingConfiguration
                  .fromMessageIfPossible(message);
              if (optionalRanking.isPresent()) {
                return optionalRanking.get().getName()
                    .equalsIgnoreCase(rankingName);
              }
              return false;
            }).findFirst()
            .orElseThrow(
                RankingNotFoundException::new));
  }

  private RankingConfigurationWithMessageId getRankingWithMessageId(TextChannel channel,
      String rankingName) {
    return RankingConfigurationWithMessageId
        .fromMessage(channel.getHistory().retrievePast(RANKING_LIMIT).complete().stream()
            .filter(message -> {
              Optional<RankingConfiguration> optionalRanking = RankingConfiguration
                  .fromMessageIfPossible(message);
              if (optionalRanking.isPresent()) {
                return optionalRanking.get().getName()
                    .equalsIgnoreCase(rankingName);
              }
              return false;
            }).findFirst()
            .orElseThrow(
                RankingNotFoundException::new));
  }

  private String getRankingName(String command) {
    String[] words = command.split("\"");
    return words[1];
  }

  private String getAccountToAdd(String command) {
    String[] words = command.split("\"");
    List<String> accountName = Arrays.asList(words).subList(2, words.length);
    return String.join(" ", accountName);
  }

  private List<String> getAccountsToAdd(String command, String rankingName) {
    String concatedAccounts = command
        .substring(command.indexOf(rankingName) + rankingName.length() + 2);
    String[] accounts = concatedAccounts.split(",");
    return Arrays.asList(accounts);
  }

  private TextChannel getConfigChannel(JDA bot) {
    return bot.getTextChannelsByName(PRIVATE_CONFIG_CHANNEL, true).stream()
        .findFirst().orElseThrow(
            ConfigChannelNotFoundException::new);
  }

}

