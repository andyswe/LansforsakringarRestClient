package lansforsakringar.api.services.internal;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.DecoderException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.web.client.RestTemplate;

import lansforsakringar.api.services.RestClient;
import lansforsakringar.api.services.internal.transfer.AuthenticateRequest;
import lansforsakringar.api.services.internal.transfer.AuthenticateResponse;
import lansforsakringar.api.services.internal.transfer.HandshakeResponse;
import lansforsakringar.api.services.internal.transfer.TokenChallengeRequest;
import lansforsakringar.api.services.internal.transfer.TokenChallengeResponse;

public class LoginService {

	private final RestTemplate restTemplate;

	public LoginService(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	public Ticket login(String customerId, String pinCode) throws NoSuchAlgorithmException, DecoderException {
		String ctoken = doHandshake();
		String utoken = authenticate(customerId, pinCode, ctoken);
		return new Ticket(ctoken, utoken);
	}

	private String doHandshake() throws NoSuchAlgorithmException, DecoderException {
		HandshakeResponse handshakeResponse = restTemplate.getForObject(RestClient.TOKEN_CHALLENGE_URL,
				HandshakeResponse.class);
		String hex = ChallengeConverter.calculateLfHash(handshakeResponse.getNumber());

		TokenChallengeRequest request = new TokenChallengeRequest();
		request.setOriginalChallenge(handshakeResponse.getNumber());
		request.setHash(hex);
		request.setChallengePair(handshakeResponse.getNumberPair());

		TokenChallengeResponse tokenChallengeResponse = restTemplate.postForObject(RestClient.TOKEN_CHALLENGE_URL,
				request, TokenChallengeResponse.class);
		return tokenChallengeResponse.getToken();
	}

	private String authenticate(String customerId, String pinCode, String ctoken) {
		List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
		interceptors.add(new LoginTokenHeaderInceptor(ctoken));
		restTemplate.setInterceptors(interceptors);

		AuthenticateRequest request = new AuthenticateRequest();
		request.setSsn(customerId);
		request.setPin(pinCode);

		AuthenticateResponse authenticateResponse = restTemplate.postForObject(RestClient.AUTHENTICATE_URL, request,
				AuthenticateResponse.class);

		String utoken = authenticateResponse.getTicket();
		return utoken;
	}

	/**
	 * Interceptor to add <code>ctoken</code> and <code>DeviceId</code> headers to a HTTP request using
	 * <code>RestTemplate</code>.
	 */
	private class LoginTokenHeaderInceptor implements ClientHttpRequestInterceptor {
		private final String ctoken;
	
		public LoginTokenHeaderInceptor(String ctoken) {
			this.ctoken = ctoken;
		}
	
		@Override
		public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
				throws IOException {
			HttpRequestWrapper requestWrapper = new HttpRequestWrapper(request);
			requestWrapper.getHeaders().add("ctoken", ctoken);
			requestWrapper.getHeaders().add("DeviceId", "f8280cf34708c7b5a8bd2ed93dcd3c8148d00000");
			return execution.execute(requestWrapper, body);
		}
	
	}

}
