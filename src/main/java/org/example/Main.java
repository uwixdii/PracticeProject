package org.example;

import org.example.Panels.SupplyCenterPanel;
import org.example.Panels.WarehousePanel;
import org.example.Panels.WorkshopPanel;
import org.example.Panels.admin.AdminPanel;

import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.SQLException;

public class Main {

    public static final String DB_NAME = "data/database.db";

    public static void main(String[] args)
    {
        SwingUtilities.invokeLater(Main::createLoginWindow);
    }

    public static void createLoginWindow() {
        JFrame frame = new JFrame("Вход в систему");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(340, 240);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel lblLogin = new JLabel("Логин:");
        JTextField txtLogin = new JTextField(20);
        JLabel lblPass = new JLabel("Пароль:");
        JPasswordField txtPass = new JPasswordField(20);
        JButton btnLogin = new JButton("Войти");

        gbc.gridx = 0; gbc.gridy = 0; frame.add(lblLogin, gbc);
        gbc.gridx = 1; frame.add(txtLogin, gbc);
        gbc.gridx = 0; gbc.gridy = 1; frame.add(lblPass, gbc);
        gbc.gridx = 1; frame.add(txtPass, gbc);
        gbc.gridx = 1; gbc.gridy = 2; frame.add(btnLogin, gbc);

        btnLogin.addActionListener(e -> {
            String login = txtLogin.getText().trim();
            String pass = new String(txtPass.getPassword()).trim();

            if (login.isEmpty() || pass.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Введите логин и пароль");
                return;
            }

            try (Connection conn = DbManager.connect(DB_NAME)) {
                User user = DbManager.authenticate(conn, login, pass);
                if (user != null) {
                    frame.dispose();
                    openMainWindow(user);
                } else {
                    JOptionPane.showMessageDialog(frame, "Неверные данные", "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(frame, "Ошибка БД:\n" + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        });

        frame.setVisible(true);
    }

    private static void openMainWindow(User user) {
        JFrame main = new JFrame("Система — " + user.getRole() + " (" + user.getUsername() + ")");
        main.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        main.setSize(1100, 750);
        main.setLocationRelativeTo(null);

        boolean isAdmin = "Админ".equals(user.getRole());

        JPanel panel;
        if (isAdmin) {
            panel = new AdminPanel(main, user);
        } else {
            switch (user.getRole()) {
                case "Цех"               -> panel = new WorkshopPanel(main, false, user);
                case "Склад"             -> panel = new WarehousePanel(main, false, user);
                case "Центр снабжения"   -> panel = new SupplyCenterPanel(main, false, user);
                default                  -> panel = new JPanel();
            }
        }

        main.add(panel);
        main.setVisible(true);
    }
}