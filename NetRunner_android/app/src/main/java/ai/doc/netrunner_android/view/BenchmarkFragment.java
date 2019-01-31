package ai.doc.netrunner_android.view;


import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import ai.doc.netrunner_android.R;

/**
 * A simple {@link Fragment} subclass.
 */
public class BenchmarkFragment extends Fragment {


    public BenchmarkFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_benchmark, container, false);
    }

}
