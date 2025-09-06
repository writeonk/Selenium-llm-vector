package com.chatbot.gov;

import com.aventstack.extentreports.Status;
import common.TestBase;
import org.testng.annotations.Test;
import pages.LoginPage;

public class LoginTest extends TestBase {

    @Test(priority = 1)
    public void VerifyAppURL() {
        log.info("Verify Chatbot Application URL");
        String url = properties.getProperty("app.url");
        openURL(url);
        test.log(Status.INFO, "Verify URL");
        log.info("Verify URL");
    }

    @Test(priority = 2)
    public void VerifyAppLogin() {
        log.info("Verify Login");

        String email = properties.getProperty("app.email");
        String password = properties.getProperty("app.password");

        LoginPage loginPage = new LoginPage(driver);

        loginPage.clickLoginUsingCreds();
        test.log(Status.INFO, "Clicked on Login Using Credentials");
        log.info("Click on Login Using Credentials button");

        loginPage.enterEmail(email);
        test.log(Status.INFO, "Entered email");
        log.info("Enter email");

        loginPage.enterPassword(password);
        test.log(Status.INFO, "Entered password");
        log.info("Enter Password");

        loginPage.clickSignIn();
        test.log(Status.INFO, "Clicked on SignIn");
        log.info("Click on Login");
    }

}