import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MyTelegramBot extends TelegramLongPollingBot {
    private static final String BOT_TOKEN = System.getenv("BOT_TOKEN");
    private static final String BOT_USERNAME = "behaplayerBot";
    private static final String DB_URL = System.getenv("DB_URL");
    private static final String DB_USER = System.getenv("DB_USER");
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");
    private static final String[][] commands = {{"/start", "Приветственное сообщение и отображение клавиатуры с командами"}, {"/signup", "Регистрация пользователя и создание записи в таблице балансов"}, {"/signin", "Авторизация пользователя (проверка регистрации)"}, {"/balance", "Показать текущий баланс пользователя"}, {"/deposit", "Пополнить баланс на указанную сумму (после команды указывается сумма)"}, {"/withdraw", "Снять указанную сумму с баланса (после команды указывается сумма)"},};
    private static String stringedCommands = "";

    @Override
    public String getBotUsername() {
        return BOT_USERNAME;
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            Message message = update.getMessage();
            String userMessage = message.getText().toLowerCase();
            Long chatId = message.getChatId();
            String username = message.getFrom().getUserName();

            switch (userMessage.split(" ")[0]) {
                case "/start":
                    sendTextMessage(chatId, "Добро пожаловать в бота!", true);
                    break;
                case "/help":
                    for (String[] command : commands) {
                        stringedCommands += command[0] + " - " + command[1] + "\n";
                    }
                    sendTextMessage(chatId, stringedCommands, true);
                    stringedCommands = "";
                    break;
                case "/signup":
                    signUp(chatId, username);
                    break;
                case "/signin":
                    signIn(chatId, username);
                    break;
                case "/balance":
                    showBalance(chatId, username);
                    break;
                case "/deposit":
                    handleDeposit(chatId, username, userMessage);
                    break;
                case "/withdraw":
                    handleWithdraw(chatId, username, userMessage);
                    break;
                default:
                    sendTextMessage(chatId, "Неизвестная команда. Попробуйте /help.", false);
            }
        }
    }

    private void signUp(Long chatId, String username) {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String query = "INSERT INTO users (username, is_authorized) VALUES (?, true) ON CONFLICT (username) DO NOTHING";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, username);
                statement.executeUpdate();
            }

            // Создание записи в balances
            String balanceQuery = "INSERT INTO balances (username, balance) VALUES (?, 0) ON CONFLICT (username) DO NOTHING";
            try (PreparedStatement balanceStatement = connection.prepareStatement(balanceQuery)) {
                balanceStatement.setString(1, username);
                balanceStatement.executeUpdate();
            }

            sendTextMessage(chatId, "Вы зарегистрированы!", true);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void signIn(Long chatId, String username) {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String query = "SELECT is_authorized FROM users WHERE username = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, username);
                ResultSet rs = statement.executeQuery();
                if (rs.next() && rs.getBoolean("is_authorized")) {
                    sendTextMessage(chatId, "Добро пожаловать!", true);
                } else {
                    sendTextMessage(chatId, "Вы не зарегистрированы. Используйте /signup.", false);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void showBalance(Long chatId, String username) {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String query = "SELECT balance FROM balances WHERE username = ?";
            try (PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, username);
                ResultSet rs = statement.executeQuery();
                if (rs.next()) {
                    sendTextMessage(chatId, "Ваш баланс: " + rs.getInt("balance") + "$", false);
                } else {
                    sendTextMessage(chatId, "Вы не зарегистрированы.", false);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void handleDeposit(Long chatId, String username, String userMessage) {
        try {
            int amount = Integer.parseInt(userMessage.split(" ")[1]);
            try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String query = "UPDATE balances SET balance = balance + ? WHERE username = ?";
                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setInt(1, amount);
                    statement.setString(2, username);
                    int rowsUpdated = statement.executeUpdate();
                    if (rowsUpdated > 0) {
                        showBalance(chatId, username);
                    } else {
                        sendTextMessage(chatId, "Вы не зарегистрированы.", false);
                    }
                }
            }
        } catch (NumberFormatException | SQLException e) {
            e.printStackTrace();
            sendTextMessage(chatId, "Введите корректную сумму для пополнения.", false);
        }
    }

    private void handleWithdraw(Long chatId, String username, String userMessage) {
        try {
            int amount = Integer.parseInt(userMessage.split(" ")[1]);
            try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String balanceQuery = "SELECT balance FROM balances WHERE username = ?";
                try (PreparedStatement statement = connection.prepareStatement(balanceQuery)) {
                    statement.setString(1, username);
                    ResultSet rs = statement.executeQuery();
                    if (rs.next() && rs.getInt("balance") >= amount) {
                        String updateQuery = "UPDATE balances SET balance = balance - ? WHERE username = ?";
                        try (PreparedStatement updateStatement = connection.prepareStatement(updateQuery)) {
                            updateStatement.setInt(1, amount);
                            updateStatement.setString(2, username);
                            updateStatement.executeUpdate();
                            sendTextMessage(chatId, "Снято " + amount + "$.", false);
                            showBalance(chatId, username);
                        }
                    } else {
                        sendTextMessage(chatId, "Недостаточно средств.", false);
                    }
                }
            }
        } catch (NumberFormatException | SQLException e) {
            e.printStackTrace();
            sendTextMessage(chatId, "Введите корректную сумму для снятия.", false);
        }
    }

    private void sendTextMessage(Long chatId, String text, boolean withKeyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        if (withKeyboard) {
            ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
            List<KeyboardRow> keyboard = new ArrayList<>();
            KeyboardRow row = new KeyboardRow();
            row.add("/balance");
            row.add("/deposit");
            row.add("/withdraw");
            keyboard.add(row);
            keyboardMarkup.setKeyboard(keyboard);
            message.setReplyMarkup(keyboardMarkup);
        }

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new MyTelegramBot());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
