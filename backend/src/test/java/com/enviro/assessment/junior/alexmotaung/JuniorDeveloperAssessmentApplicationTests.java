package com.enviro.assessment.junior.alexmotaung;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:startup-tests;DB_CLOSE_DELAY=-1")
class JuniorDeveloperAssessmentApplicationTests {

	@Test
	void contextLoads() {
	}

}
