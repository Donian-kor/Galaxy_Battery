package com.donian.galaxy_battery

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * 인접한 텍스트 폰트의 획 굵기에 맞추어 아이콘 두께를 자동으로 보정하여 그리는 컴포저블.
 *
 * Material Symbols 벡터의 획 두께와 폰트의 대문자 'I' 세로획 굵기를 동적으로 측정하여,
 * 차이만큼 아웃라인 스트로크를 추가하거나 깎아내어 글자와 아이콘의 시각적 굵기를 일치시킨다.
 */
@Composable
fun MatchedIcon(
    @DrawableRes res: Int,
    style: TextStyle,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val vectorVector = ImageVector.vectorResource(res)
    val shape = remember(res) { IconShapeRegistry.getOrCreateShape(res, vectorVector) }

    val resolver = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val shouldMirror = vectorVector.autoMirror && isRtl

    val calculatedFontStem: Float? = if (style.fontSize.isSpecified) {
        val fontKey = FontKeySpec(style.fontFamily, style.fontWeight, style.fontStyle)
        val stemRatio = remember(fontKey) {
            IconShapeRegistry.getOrCreateFontRatio(fontKey) {
                measureFontStemRatio(
                    TextMeasurer(resolver, density, LayoutDirection.Ltr, cacheSize = 0),
                    style
                )
            }
        }
        with(density) { stemRatio * style.fontSize.toPx() }
    } else {
        null
    }

    Canvas(
        modifier.graphicsLayer {
            val strokeDelta = shape.calculateStrokeDelta(size, calculatedFontStem)
            compositingStrategy = if (strokeDelta < 0f) {
                CompositingStrategy.Offscreen
            } else {
                CompositingStrategy.Auto
            }
        }
    ) {
        val scaleFactor = shape.computeScaleFactor(size)
        val strokeDelta = shape.calculateStrokeDelta(size, calculatedFontStem)
        val flipHorizontal = if (shouldMirror) -1f else 1f

        val offsetX = (size.width - shape.width * scaleFactor) * 0.5f
        val offsetY = (size.height - shape.height * scaleFactor) * 0.5f

        scale(flipHorizontal, 1f) {
            translate(offsetX, offsetY) {
                scale(scaleFactor, scaleFactor, pivot = Offset.Zero) {
                    val normalizedDelta = strokeDelta / scaleFactor
                    for (path in shape.paths) {
                        drawPath(path, tint)
                        when {
                            strokeDelta > 0f -> {
                                drawPath(
                                    path = path,
                                    color = tint,
                                    style = Stroke(width = normalizedDelta, join = StrokeJoin.Round)
                                )
                            }
                            strokeDelta < 0f -> {
                                drawPath(
                                    path = path,
                                    color = Color.Black,
                                    style = Stroke(width = -normalizedDelta, join = StrokeJoin.Round),
                                    blendMode = BlendMode.Clear
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class FontKeySpec(
    val family: Any?,
    val weight: Any?,
    val style: Any?
)

/** 아이콘 및 폰트 획 측정값 캐시 레지스트리. */
private object IconShapeRegistry {
    private val iconCache = ConcurrentHashMap<Int, IconShape>()
    private val fontCache = ConcurrentHashMap<FontKeySpec, Float>()

    fun getOrCreateShape(resId: Int, vector: ImageVector): IconShape {
        return iconCache.getOrPut(resId) { IconShape.createFromVector(vector) }
    }

    fun getOrCreateFontRatio(key: FontKeySpec, compute: () -> Float): Float {
        return fontCache.getOrPut(key, compute)
    }
}

/** 백터 아이콘의 경로 정보 및 측정된 상대 획 두께 보관 클래스. */
private class IconShape(
    val paths: List<Path>,
    val width: Float,
    val height: Float,
    val relativeStem: Float
) {
    fun computeScaleFactor(targetSize: Size): Float {
        val scaleX = targetSize.width / width
        val scaleY = targetSize.height / height
        return min(scaleX, scaleY)
    }

    fun calculateStrokeDelta(targetSize: Size, targetFontStemPx: Float?): Float {
        if (targetFontStemPx == null) return 0f
        val iconStemPx = relativeStem * height * computeScaleFactor(targetSize)
        val delta = targetFontStemPx - iconStemPx
        val maxBound = iconStemPx * 0.5f
        return delta.coerceIn(-maxBound, maxBound)
    }

    companion object {
        fun createFromVector(vector: ImageVector): IconShape {
            val pathList = mutableListOf<Path>()
            extractPaths(vector.root, 0f, 0f, pathList)
            val vWidth = vector.viewportWidth
            val vHeight = vector.viewportHeight
            val measuredStem = estimateIconStemRatio(pathList, vWidth, vHeight)
            return IconShape(pathList, vWidth, vHeight, measuredStem)
        }

        private fun extractPaths(
            group: VectorGroup,
            offsetX: Float,
            offsetY: Float,
            outPaths: MutableList<Path>
        ) {
            for (node in group) {
                when (node) {
                    is VectorPath -> {
                        val path = PathParser().addPathNodes(node.pathData).toPath()
                        path.fillType = node.pathFillType
                        path.translate(Offset(offsetX, offsetY))
                        outPaths.add(path)
                    }
                    is VectorGroup -> {
                        extractPaths(
                            node,
                            offsetX + node.translationX,
                            offsetY + node.translationY,
                            outPaths
                        )
                    }
                }
            }
        }

        /** 비트맵 래스터 렌더링을 이용하여 획 면적 대 둘레비로 두께 비율을 측정한다. */
        private fun estimateIconStemRatio(paths: List<Path>, viewportWidth: Float, viewportHeight: Float): Float {
            val targetHeightPx = 240
            val scaleFactor = targetHeightPx / viewportHeight
            val targetWidthPx = max(1, (viewportWidth * scaleFactor).toInt())

            val bitmap = ImageBitmap(targetWidthPx, targetHeightPx)
            val canvas = Canvas(bitmap)

            CanvasDrawScope().draw(
                Density(1f),
                LayoutDirection.Ltr,
                canvas,
                Size(targetWidthPx.toFloat(), targetHeightPx.toFloat())
            ) {
                scale(scaleFactor, scaleFactor, pivot = Offset.Zero) {
                    for (path in paths) {
                        drawPath(path, Color.Black)
                    }
                }
            }

            val pixelMap = bitmap.toPixelMap()
            var activePixelCount = 0
            var boundaryPixelCount = 0

            for (y in 0 until targetHeightPx) {
                for (x in 0 until targetWidthPx) {
                    if (pixelMap[x, y].alpha <= 0.5f) continue
                    activePixelCount++
                    val isEdge = x == 0 || x == targetWidthPx - 1 ||
                            y == 0 || y == targetHeightPx - 1 ||
                            pixelMap[x - 1, y].alpha <= 0.5f ||
                            pixelMap[x + 1, y].alpha <= 0.5f ||
                            pixelMap[x, y - 1].alpha <= 0.5f ||
                            pixelMap[x, y + 1].alpha <= 0.5f
                    if (isEdge) {
                        boundaryPixelCount++
                    }
                }
            }

            return if (boundaryPixelCount == 0) {
                0.1f
            } else {
                (2f * activePixelCount) / (boundaryPixelCount * targetHeightPx)
            }
        }
    }
}

/** 폰트 대문자 'I'의 획 굵기 비율 측정. */
private fun measureFontStemRatio(measurer: TextMeasurer, style: TextStyle): Float {
    val sampleStyle = style.copy(fontSize = 100.sp, color = Color.Black, letterSpacing = 0.sp)
    val textLayoutResult = measurer.measure("I", sampleStyle, density = Density(1f, 1f))

    val bitmapWidth = max(1, textLayoutResult.size.width)
    val bitmapHeight = max(1, textLayoutResult.size.height)
    val bitmap = ImageBitmap(bitmapWidth, bitmapHeight)
    val canvas = Canvas(bitmap)

    CanvasDrawScope().draw(
        Density(1f),
        LayoutDirection.Ltr,
        canvas,
        Size(bitmapWidth.toFloat(), bitmapHeight.toFloat())
    ) {
        drawText(textLayoutResult)
    }

    val pixelMap = bitmap.toPixelMap()
    val sampleY = (textLayoutResult.firstBaseline - 35f).toInt().coerceIn(0, bitmapHeight - 1)

    var currentRunLength = 0
    var maxRunLength = 0

    for (x in 0 until bitmapWidth) {
        if (pixelMap[x, sampleY].alpha > 0.5f) {
            currentRunLength++
            if (currentRunLength > maxRunLength) {
                maxRunLength = currentRunLength
            }
        } else {
            currentRunLength = 0
        }
    }

    return if (maxRunLength == 0) 0.09f else maxRunLength / 100f
}
