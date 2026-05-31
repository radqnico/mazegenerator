package it.nicoloscialpi.mazegenerator.dialog;

public class DialogHandlerProvider {
    private static DialogHandler instance;

    public static DialogHandler getHandler() {
        if (instance != null) {
            return instance;
        }

        try {
            Class<?> paperHandlerClass = Class.forName("it.nicoloscialpi.mazegenerator.dialog.impl.PaperDialogHandler");
            instance = (DialogHandler) paperHandlerClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            instance = new NoOpDialogHandler();
        }

        return instance;
    }
}