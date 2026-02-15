package com.example.grabitTest

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * MainActivity/탭 간 공유 상태.
 * 관리자 탭 또는 마이페이지(자주/최근 찾은 상품)에서 상품 선택 시 Home으로 타겟 전달 및 탐지 시작.
 * 탭 전환 시 전역 상태 초기화 트리거.
 */
class SharedViewModel : ViewModel() {

    /** 선택된 검색 타겟 클래스. HomeFragment가 구독하여 탐지 시작 (관리자/마이페이지 공통) */
    private val _selectedSearchTarget = MutableLiveData<String?>(null)
    val selectedSearchTarget: LiveData<String?> = _selectedSearchTarget

    /** 상품 선택 시 호출 (관리자 또는 자주/최근 찾은 상품). Home으로 이동 후 탐지 시작 */
    fun selectTargetForSearch(targetLabel: String) {
        _selectedSearchTarget.value = targetLabel
    }

    /** HomeFragment가 이벤트를 처리한 후 소비 */
    fun consumeSelectedSearchTarget() {
        _selectedSearchTarget.value = null
    }

    /** 볼륨 키 롱프레스 시 음성 인식 등 실행 요청. MainActivity가 호출, HomeFragment가 구독 */
    private val _volumeLongPressTrigger = MutableLiveData<Unit?>(null)
    val volumeLongPressTrigger: LiveData<Unit?> = _volumeLongPressTrigger

    fun triggerVolumeLongPress() {
        _volumeLongPressTrigger.value = Unit
    }

    fun consumeVolumeLongPressTrigger() {
        _volumeLongPressTrigger.value = null
    }
}
