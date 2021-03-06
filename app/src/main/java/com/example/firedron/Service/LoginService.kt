package com.example.firedron.Service
import com.example.firedron.dto.Token
import retrofit2.Call
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

interface LoginService {

    @FormUrlEncoded
    @POST("auth/token/login/")
    fun requestLogin(   //input 정의
        @Field("username") username:String,
        @Field("password") password:String
    ) : Call<Token> //output 정의
}