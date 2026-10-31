package io.github.marler8997.harpertube;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView text = new TextView(this);
        text.setText("HarperTube");
        text.setTextSize(48);
        text.setGravity(Gravity.CENTER);
        setContentView(text);
    }
}
