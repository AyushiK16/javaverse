class StaticBox {
    int value;

    StaticBox(int value) {
        this.value = value;
    }
}

public class StaticReferenceDemo {
    static StaticBox saved;

    public static void main(String[] args) {
        StaticBox local = new StaticBox(42);

        saved = local;
        local = null;

        int value = saved.value;
        System.out.println(value);

        saved = null;
        System.out.println("Static reference cleared");
    }
}
