package com.example.grabit.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import com.example.grabit.R  // 👈 이 줄이 없어서 에러난 겁니다!

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 1. 화면 껍데기 연결
        setContentView(R.layout.activity_main)

        // 2. 네비게이션(화면 이동 담당자) 연결 확인
        // (activity_main.xml에 있는 FragmentContainerView를 찾아옴)
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment

        // 혹시나 해서 null 체크 (앱 안 죽게)
        if (navHostFragment == null) {
            // 로그라도 남기거나 예외처리
        }
    }
}