package com.example.coroutinestudy

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment

/**
 * "Fragment 에서 띄운 다이얼로그"가 호스트(Fragment/Activity) 생명주기를 바꾸는지 비교용.
 * Activity 에서 띄운 다이얼로그는 MainActivity 의 Section 4 버튼(AlertDialog/BottomSheet)과 비교한다.
 *
 * 로그를 [Fragment] / [DialogFragment] / [Main] 으로 구분해서, DialogFragment 가 떠 있는 동안
 * 호스트 Fragment 또는 Main Activity 가 onPause 를 타는지 관찰한다.
 */
class LifecycleTestFragment : Fragment() {

    private val tag = LifecycleTestApp.TAG

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        Log.e(tag, "[Fragment] onCreateView()")
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(requireContext()).apply {
                text = "[Fragment 영역]"
            })
            addView(Button(requireContext()).apply {
                text = "Fragment 에서 DialogFragment 열기"
                setOnClickListener {
                    Log.e(tag as String?, "[DialogFragment] show() 호출")
                    MyDialogFragment().show(childFragmentManager, "myDialog")
                }
            })
        }
    }

    override fun onStart() { super.onStart(); Log.e(tag, "[Fragment] onStart()") }
    override fun onResume() { super.onResume(); Log.e(tag, "[Fragment] onResume()") }
    override fun onPause() { super.onPause(); Log.e(tag, "[Fragment] onPause()") }
    override fun onStop() { super.onStop(); Log.e(tag, "[Fragment] onStop()") }
    override fun onDestroyView() { super.onDestroyView(); Log.e(tag, "[Fragment] onDestroyView()") }
}

/** Fragment 에서 띄우는 표준 DialogFragment (AlertDialog 기반) */
class MyDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
            .setTitle("DialogFragment")
            .setMessage("이게 떠 있는 동안 [Fragment] / [Main] onPause 가 찍히는지 보세요")
            .setPositiveButton("닫기") { d, _ -> d.dismiss() }
            .create()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        Log.e(LifecycleTestApp.TAG, "[DialogFragment] dismiss")
    }
}
