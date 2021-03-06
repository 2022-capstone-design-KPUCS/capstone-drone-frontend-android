package com.example.firedron.Activity

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.annotation.RequiresApi
import com.example.firedron.R
import com.example.firedron.Service.FlyService
import com.example.firedron.Service.MapService
import com.example.firedron.dto.FlightPath
import com.example.firedron.dto.MResponse
import com.example.firedron.dto.Token
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.LocationTrackingMode
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.PathOverlay
import com.naver.maps.map.util.FusedLocationSource
import kotlinx.android.synthetic.main.activity_map.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

class MapActivity : Activity(), OnMapReadyCallback {

    private lateinit var mapView: com.naver.maps.map.MapView
    private lateinit var locationSource: FusedLocationSource // 위치를 반환하는 구현체
    private lateinit var naverMap: NaverMap
    private lateinit var token: Token
    private lateinit var flight_id: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        window.decorView.apply {
            systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN
        } //하단바, 상단바 모두 숨김모드

        val intent: Intent = getIntent()
        token = intent.getParcelableExtra("TOKEN")!!

        mapView = findViewById(R.id.map_view)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        locationSource = FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE)

    }
    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        if (locationSource.onRequestPermissionsResult(requestCode, permissions,
                grantResults)) {
            if (!locationSource.isActivated) { // 권한 거부됨
                naverMap.locationTrackingMode = LocationTrackingMode.None
            }
            return
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onMapReady(@NonNull naverMap: NaverMap) {
        this.naverMap = naverMap

        naverMap.locationSource = locationSource
        naverMap.locationTrackingMode = LocationTrackingMode.Follow

        val marker2: MutableList<Marker> = ArrayList() // UI
        var marker : MutableList<LatLng> = ArrayList() // POST data

        naverMap.setOnMapClickListener { point, coord ->
            Toast.makeText(
                this, "${coord.latitude}, ${coord.longitude}",
                Toast.LENGTH_SHORT
            ).show()
            val path = PathOverlay()
            marker.add(LatLng(coord.latitude, coord.longitude))
            //한국공대 좌표 : 37.3340, 126.7337
            Log.d("LATLNG", marker.toString())
            val marker_object: Marker = Marker()
            marker_object.position = LatLng(coord.latitude, coord.longitude)
            marker_object.map = naverMap
            marker_object.iconTintColor = Color.RED
            marker2.add(marker_object)
        }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val newRequest: Request = chain.request().newBuilder()
                .header("Authorization", "Token ${token.auth_token}")
                .build()
            chain.proceed(newRequest)
        }.build()
        val retrofit = Retrofit.Builder()
            .baseUrl("http://"+getString(R.string.AWS)+":8000/")
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
        val retrofitflight = Retrofit.Builder()
            .baseUrl("https://9233-118-222-85-227.jp.ngrok.io/")
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val mapPostService = retrofit.create(MapService::class.java) // 값보내기
        val flyServiceService = retrofitflight.create(FlyService::class.java)
        val pathArray = JSONArray()
        val mapGetService = retrofit.create(MapService::class.java) //값 불러오기
        post_button.setOnClickListener {
            for(x in marker) {
                val coordinates = JSONObject()
                coordinates.put("lat", x.latitude)
                coordinates.put("lng", x.longitude)
                pathArray.put(coordinates)
            }
            flight_id = intent.getStringExtra("FLIGHT").toString()
            Log.w("FLIGHT_ID", flight_id)

            if (flight_id == "null") {
                mapPostService.responseMap()
                    .enqueue(object : Callback<MResponse> {
                        override fun onResponse(
                            call: Call<MResponse>,
                            response: Response<MResponse>
                        ) {
                            if (response.isSuccessful) {
                                flight_id = response.body()?.results?.get(0)?.id.toString()
                                Log.w("FLIGHT_ID", flight_id)

                                mapPostService.requestMap(flight_id, pathArray)
                                    .enqueue(object : Callback<FlightPath> {
                                        override fun onResponse(
                                            call: Call<FlightPath>,
                                            response: Response<FlightPath>
                                        ) {
                                            if (response.isSuccessful) {
                                                val mapResponse = response.body()
                                                Log.w("POSTSUCCESS", mapResponse.toString())
                                                val intent = Intent(this@MapActivity, LiveActivity::class.java)
                                                startActivity(intent)
                                            } else {
                                                Log.w("RESPONSEERROR", response.toString())
                                            }
                                        }

                                        override fun onFailure(call: Call<FlightPath>, t: Throwable) {
                                            Log.d("FAILED", t.message.toString())
                                        }

                                    })
                            } else {
                                Log.w("RESPONSEERROR", response.toString())
                            }
                        }

                        override fun onFailure(call: Call<MResponse>, t: Throwable) {
                            Log.d("FAILED", t.message.toString())
                        }

                    })
            } else {
                mapPostService.requestMap(flight_id, pathArray)
                    .enqueue(object : Callback<FlightPath> {
                        override fun onResponse(
                            call: Call<FlightPath>,
                            response: Response<FlightPath>
                        ) {
                            if (response.isSuccessful) {
                                val mapResponse = response.body()
                                Log.w("POSTSUCCESS", mapResponse.toString())

                            } else {
                                Log.w("RESPONSEERROR", response.toString())
                            }
                        }

                        override fun onFailure(call: Call<FlightPath>, t: Throwable) {
                            Log.d("FAILED", t.message.toString())
                        }

                    })
            }
            flyServiceService.requestFlight().enqueue(object: Callback<String>{
                override fun onResponse(call: Call<String>, response: Response<String>) {
                    if (response.isSuccessful) {
                        Log.d("GOOD", "GOOD")
                    } else {
                        Log.w("GETERROR", response.body().toString())
                    }
                }

                override fun onFailure(call: Call<String>,  t: Throwable) {
                    Log.d("FAILED", t.message.toString())
                }

            })

            val intent = Intent(this@MapActivity, LiveActivity::class.java)
            startActivity(intent)
        }
        load_button.setOnClickListener {
            mapGetService.responseMap().enqueue(object: Callback<MResponse>{
                override fun onResponse(call: Call<MResponse>, response: Response<MResponse>) {
                    if (response.code() == 200) {
                        val mapResponse = response.body()?.results
                        val marker_put_list: MutableList<Marker> = ArrayList()
                        Log.d("MAPCHECK", mapResponse.toString())
                        for(x in mapResponse?.last()?.flight_path!!) { //marker 불러오기
                            val marker_put = Marker()
                            val coords =JSONObject()
                            coords.put("lat", x.lat)
                            coords.put("lng", x.lng)
                            pathArray.put(coords)
                            marker_put.position = LatLng(x.lat, x.lng)
                            marker_put.map = naverMap
                            marker_put.iconTintColor = Color.YELLOW
                            marker_put_list.add(marker_put)
                        }
                        Log.w("GETSUCCESS", mapResponse.toString())
                    } else {
                        Log.w("GETERROR", response.toString())
                        Log.w("GETERROR", response.body().toString())
                    }
                }

                override fun onFailure(call: Call<MResponse>, t: Throwable) {
                    Log.d("FAILED", t.message.toString())
                }

            })


        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }


    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }


    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1000
    }
}
