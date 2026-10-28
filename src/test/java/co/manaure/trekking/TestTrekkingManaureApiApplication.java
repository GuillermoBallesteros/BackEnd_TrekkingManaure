package co.manaure.trekking;

import org.springframework.boot.SpringApplication;

public class TestTrekkingManaureApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(TrekkingManaureApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
