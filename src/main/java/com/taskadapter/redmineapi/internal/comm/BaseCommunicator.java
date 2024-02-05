package com.taskadapter.redmineapi.internal.comm;

import com.taskadapter.redmineapi.RedmineException;
import com.taskadapter.redmineapi.RedmineFormatException;
import com.taskadapter.redmineapi.RedmineTransportException;
import org.apache.http.*;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class BaseCommunicator implements Communicator<HttpResponse> {

	private static final Integer STATUS_CODE_EXCEEDED_LIMITS = 429;

	private final Logger logger = LoggerFactory.getLogger(BaseCommunicator.class);

	private final HttpClient client;

    public BaseCommunicator(HttpClient client) {
        this.client = client;
    }

	// TODO lots of usages process 404 code themselves, but some don't.
	// check if we can process 404 code in this method instead of forcing
	// clients to deal with it.

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.taskadapter.redmineapi.internal.comm.Communicator#sendRequest(org.apache.http
	 * .HttpRequest)
	 */
	@Override
	public <R> R sendRequest(HttpRequest request,
			ContentHandler<HttpResponse, R> handler) throws RedmineException {
		logger.debug(request.getRequestLine().toString());

		request.addHeader("Accept-Encoding", "gzip");
		final HttpClient httpclient = client;
		try {
			HttpResponse httpResponse = httpclient
					.execute((HttpUriRequest) request);

			// Handle rate limits for API requests that have been introduced since EasyRedmine version 11:
			// https://www.easyredmine.com/documentation-of-easy-redmine/article/release-notes-for-version-11-0#Throttling%20API
			while (httpResponse.getStatusLine().getStatusCode() == STATUS_CODE_EXCEEDED_LIMITS) {
				EntityUtils.consume(httpResponse.getEntity());
				httpResponse = httpclient.execute((HttpUriRequest) request);
			}

			try {
				return handler.processContent(httpResponse);
			} finally {
				EntityUtils.consume(httpResponse.getEntity());

			}
		} catch (ClientProtocolException e1) {
			throw new RedmineFormatException(e1);
		} catch (IOException e1) {
			throw new RedmineTransportException("Cannot fetch data from "
					+ getMessageURI(request) + " : "
							+ e1.toString(), e1);
		}
	}

	private String getMessageURI(HttpRequest request) {
		final String uri = request.getRequestLine().getUri();
		final int paramsIndex = uri.indexOf('?');
		if (paramsIndex >= 0)
			return uri.substring(0, paramsIndex);
		return uri;
	}
}
