package io.bdeploy.launcher.cli.branding;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.Panel;

class PanelDoubleBuffered extends Panel {

    private static final long serialVersionUID = 1L;
    private int panelWidth;
    private int panelHeight;
    private transient Image offscreenImage;
    private transient Graphics offscreenGraphics;

    public PanelDoubleBuffered() {
        super();
    }

    @Override
    public void update(final Graphics g) {
        paint(g);
    }

    @Override
    public void paint(final Graphics g) {
        super.paint(g);
        // checks the buffersize with the current panelsize
        // or initialises the image with the first paint
        if (panelWidth != getSize().width || panelHeight != getSize().height || offscreenImage == null
                || offscreenGraphics == null) {
            resetBuffer();
        }
        if (offscreenGraphics != null) {
            //this clears the offscreen image, not the onscreen one
            offscreenGraphics.clearRect(0, 0, panelWidth, panelHeight);
            //calls the paintbuffer method with
            //the offscreen graphics as a param
            paintBuffer(offscreenGraphics);
            //we finaly paint the offscreen image onto the onscreen image
            g.drawImage(offscreenImage, 0, 0, this);
        }
    }

    private void resetBuffer() {
        // always keep track of the image size
        panelWidth = getSize().width;
        panelHeight = getSize().height;
        // clean up the previous image
        if (offscreenGraphics != null) {
            offscreenGraphics.dispose();
        }
        if (offscreenImage != null) {
            offscreenImage.flush();
        }
        // create the new image with the size of the panel
        offscreenImage = createImage(panelWidth, panelHeight);
        offscreenGraphics = offscreenImage.getGraphics();
    }

    public void paintBuffer(final Graphics g) {
        // in classes extended from this one, add something to paint here!
        // always remember, g is the offscreen graphics
    }
}