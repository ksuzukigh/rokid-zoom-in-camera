package io.github.ksuzukigh.rokidzoomincamera;

final class PreviewGeometry {
    static final class Layout {
        final int width;
        final int height;

        Layout(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    private PreviewGeometry() {}

    static Layout centerCrop(int viewWidth, int viewHeight,
                             int bufferWidth, int bufferHeight,
                             boolean swapped) {
        if (viewWidth <= 0 || viewHeight <= 0 || bufferWidth <= 0 || bufferHeight <= 0) {
            return new Layout(Math.max(viewWidth, 1), Math.max(viewHeight, 1));
        }
        int naturalWidth = swapped ? bufferHeight : bufferWidth;
        int naturalHeight = swapped ? bufferWidth : bufferHeight;
        double scale = Math.max(
                (double) viewWidth / naturalWidth,
                (double) viewHeight / naturalHeight
        );
        return new Layout(
                Math.max(viewWidth, (int) Math.ceil(naturalWidth * scale)),
                Math.max(viewHeight, (int) Math.ceil(naturalHeight * scale))
        );
    }
}
