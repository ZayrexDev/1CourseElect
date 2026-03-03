package xyz.zcraft;

import xyz.zcraft.forms.Login;

public class Main {
    static void main() {
        com.formdev.flatlaf.FlatDarculaLaf.setup();
        final User user = new Login().requestLogin();
        System.out.println(user);
    }
}
