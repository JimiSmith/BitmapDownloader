package za.co.immedia.bitmapdownloaderexample;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ListView;

public class ListActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        
        ListView listView = (ListView)findViewById(R.id.listView);
        ImageAdapter imageAdapter = new ImageAdapter();
        listView.setAdapter(imageAdapter);
    }
}
