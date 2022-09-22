package es.uma.mi.firma.signature;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface ApiInterface {
    @GET("posts")
    Call<Post[]> getPosts();

    @GET("posts")
    Call<ResponseBody> getRawResponse();

    @POST("posts/{post}/sign")
    Call<ResponseBody> signPost(@Path("post") int postId, @Body SignRequest signature);
}
