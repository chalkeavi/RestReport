import java.io.UnsupportedEncodingException;

import org.apache.commons.codec.binary.Base64;

public class EncryptPassword {
	public static final String USERNAME = "Avinash";
	public static final String PASSWORD = "Chalke";

	public static void main(String[] args) throws UnsupportedEncodingException {

		// If you want to use encode only password and then decode , then use below code
		String password = PASSWORD;
		System.out.println(Base64.encodeBase64String(password.getBytes("UTF-8")));

		// If you want to use Encrypted username and password use below code
		String userpass = USERNAME + ":" + PASSWORD;
		System.out.println(Base64.encodeBase64String(userpass.getBytes("UTF-8")));
	}

}
