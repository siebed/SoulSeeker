# SoulSeeker
Android app showing a 'smoke like' effect based on you happiness. 

Soul Seeker uses the [Mobile Vision](https://developers.google.com/vision/introduction) library to detect faces and extract the viewers happines.

It simulates glowing particles by first calculating the accumalted luminens of the pixels on screen and then mapping them with a color pallet to pixels in a Bitmap object.
The color pallet changes colors constantly by cycling through R,G,B channels.
By using both scaling and bluring using RenderScript it creates a smoke like effect.

The structure that is rendered is created by simulating brownian motion to create a particle structure. The structure is then mirrored and will rotate slowly around its center point. in its starting point 


## More Info
If you are curious, here is a screenshot of the app running:
[[https://github.com/siebed/SoulSeeker/blob/master/screenshot_full.png|alt=app running]]
[[https://github.com/siebed/SoulSeeker/blob/master/screenshot_custom.png|alt=app running with custom settings]]

you can see it in action here:

[running on several devices](https://youtu.be/zVoKHC7ecvI) 

[running as a Daydream with custom settings] (https://youtu.be/LMCs4JYlrJw)
