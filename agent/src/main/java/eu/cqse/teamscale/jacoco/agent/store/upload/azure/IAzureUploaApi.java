package eu.cqse.teamscale.jacoco.agent.store.upload.azure;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.http.Body;
import retrofit2.http.HEAD;
import retrofit2.http.HeaderMap;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.QueryMap;

import java.io.IOException;
import java.util.Map;

public interface IAzureUploaApi {
	@PUT("{path}")
	public Call<ResponseBody> put(
			@Path(value = "path", encoded = true) String path,
			@HeaderMap Map<String, String> headers,
			@QueryMap Map<String, String> query
	);

	@PUT("{path}")
	public Call<ResponseBody> putData(
			@Path(value = "path", encoded = true) String path,
			@HeaderMap Map<String, String> headers,
			@QueryMap Map<String, String> query,
			@Body RequestBody content
	);

	@HEAD("{path}")
	public Call<Void> head(
			@Path(value = "path", encoded = true) String path,
			@HeaderMap Map<String, String> headers,
			@QueryMap Map<String, String> query
	);


	public default void printResponse(Response<ResponseBody> response) {
		try {
			System.out.println(response.errorBody().string() + "\n");
			System.out.println(response.raw().request().headers());
			System.out.println(response.raw().request().url());
		} catch (NullPointerException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
