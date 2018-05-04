package Frontend;

public enum States {

    PREPARING("preparing"),
    READY("ready");

    private final String message;

    /**
     * Constructor of States.
     *
     * @param message
     */
    States(String message) {
        this.message = message;
    }

    /**
     * Return the state.
     *
     * @return String
     */
    @Override
    public String toString() {
        return this.message;
    }
}
