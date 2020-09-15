package net.railwaynet.logdelivery.strolr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class FederationsApplication extends WebSecurityConfigurerAdapter implements WebMvcConfigurer {

	public static void main(String[] args) {
		SpringApplication.run(FederationsApplication.class, args);
	}

	@Override
	protected void configure(HttpSecurity http) throws Exception{
		http.authorizeRequests().antMatchers("/").permitAll();
	}
}
