package pages;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

public class LoginPage {

    WebDriver driver;

    public LoginPage(WebDriver driver) {
        this.driver = driver;
        PageFactory.initElements(driver, this);
    }

    @FindBy(xpath = "//button[normalize-space()='Login using Credentials']")
    private WebElement btnLoginUsingCreds;

    @FindBy(id = "email")
    private WebElement txtEmail;

    @FindBy(id = "password")
    private WebElement txtPassword;

    @FindBy(xpath = "//button[@type='submit' and normalize-space()='Sign in']")
    private WebElement btnSignIn;

    @FindBy(id = "chat-container")
    private static WebElement chatBoxContainer;

    public static boolean isChatWidgetDisplayed() {
        return chatBoxContainer.isDisplayed();
    }

    public void clickLoginUsingCreds() {
        btnLoginUsingCreds.click();
    }

    public void enterEmail(String email) {
        txtEmail.sendKeys(email);
    }

    public void enterPassword(String password) {
        txtPassword.sendKeys(password);
    }

    public void clickSignIn() {
        btnSignIn.click();
    }
}