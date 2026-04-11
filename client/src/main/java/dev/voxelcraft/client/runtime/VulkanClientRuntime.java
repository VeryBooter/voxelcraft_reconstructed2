package dev.voxelcraft.client.runtime;

import dev.voxelcraft.client.GameClient;
import dev.voxelcraft.client.platform.InputState;
import dev.voxelcraft.client.render.ChunkMesher;
import dev.voxelcraft.client.render.Frustum;
import dev.voxelcraft.client.render.Mesh;
import dev.voxelcraft.client.render.Vec3;
import dev.voxelcraft.core.block.BlockDef;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkFormatProperties;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;
import org.lwjgl.vulkan.VkViewport;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import static org.lwjgl.glfw.GLFW.GLFW_CLIENT_API;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_1;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_2;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_3;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_4;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_5;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_6;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_7;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_E;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_1;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_2;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_3;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_4;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_5;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_6;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_7;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_O;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Q;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_R;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_V;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_X;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Z;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_NO_API;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.glfw.GLFW.GLFW_RESIZABLE;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwPostEmptyEvent;
import static org.lwjgl.glfw.GLFW.glfwSetCursorPosCallback;
import static org.lwjgl.glfw.GLFW.glfwSetFramebufferSizeCallback;
import static org.lwjgl.glfw.GLFW.glfwSetInputMode;
import static org.lwjgl.glfw.GLFW.glfwSetKeyCallback;
import static org.lwjgl.glfw.GLFW.glfwSetMouseButtonCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose;
import static org.lwjgl.glfw.GLFW.glfwSetWindowTitle;
import static org.lwjgl.glfw.GLFW.glfwShowWindow;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWaitEvents;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;
import static org.lwjgl.glfw.GLFWVulkan.glfwCreateWindowSurface;
import static org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions;
import static org.lwjgl.glfw.GLFWVulkan.glfwVulkanSupported;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.vulkan.KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_ERROR_SURFACE_LOST_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR;
import static org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkAcquireNextImageKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkCreateSwapchainKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkDestroySwapchainKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkGetSwapchainImagesKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkQueuePresentKHR;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_API_VERSION_1_0;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_CLEAR;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_STORE_OP_STORE;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_SRC_ALPHA;
import static org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMPONENT_SWIZZLE_IDENTITY;
import static org.lwjgl.vulkan.VK10.VK_CULL_MODE_NONE;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
import static org.lwjgl.vulkan.VK10.VK_FENCE_CREATE_SIGNALED_BIT;
import static org.lwjgl.vulkan.VK10.VK_FALSE;
import static org.lwjgl.vulkan.VK10.VK_FILTER_NEAREST;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_B8G8R8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_D24_UNORM_S8_UINT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT_S8_UINT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_B8G8R8A8_SRGB;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32A32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_SRGB;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_STENCIL_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TILING_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_SAMPLED_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_LOGIC_OP_COPY;
import static org.lwjgl.vulkan.VK10.VK_MAKE_VERSION;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_A_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_B_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_G_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_R_BIT;
import static org.lwjgl.vulkan.VK10.VK_FRONT_FACE_CLOCKWISE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_POLYGON_MODE_FILL;
import static org.lwjgl.vulkan.VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_GRAPHICS_BIT;
import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;
import static org.lwjgl.vulkan.VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
import static org.lwjgl.vulkan.VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST;
import static org.lwjgl.vulkan.VK10.VK_COMPARE_OP_LESS_OR_EQUAL;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_CONCURRENT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
import static org.lwjgl.vulkan.VK10.VK_SUBPASS_CONTENTS_INLINE;
import static org.lwjgl.vulkan.VK10.VK_SUBPASS_EXTERNAL;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.VK_TIMEOUT;
import static org.lwjgl.vulkan.VK10.VK_TRUE;
import static org.lwjgl.vulkan.VK10.VK_VERTEX_INPUT_RATE_VERTEX;
import static org.lwjgl.vulkan.VK10.vkAllocateCommandBuffers;
import static org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkAllocateMemory;
import static org.lwjgl.vulkan.VK10.vkBeginCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkBindBufferMemory;
import static org.lwjgl.vulkan.VK10.vkBindImageMemory;
import static org.lwjgl.vulkan.VK10.vkCmdBindDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkCmdBindPipeline;
import static org.lwjgl.vulkan.VK10.vkCmdBindVertexBuffers;
import static org.lwjgl.vulkan.VK10.vkCmdBeginRenderPass;
import static org.lwjgl.vulkan.VK10.vkCmdCopyBufferToImage;
import static org.lwjgl.vulkan.VK10.vkCmdDraw;
import static org.lwjgl.vulkan.VK10.vkCmdEndRenderPass;
import static org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier;
import static org.lwjgl.vulkan.VK10.vkCreateBuffer;
import static org.lwjgl.vulkan.VK10.vkCreateCommandPool;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkCreateDevice;
import static org.lwjgl.vulkan.VK10.vkCreateFence;
import static org.lwjgl.vulkan.VK10.vkCreateFramebuffer;
import static org.lwjgl.vulkan.VK10.vkCreateGraphicsPipelines;
import static org.lwjgl.vulkan.VK10.vkCreateImage;
import static org.lwjgl.vulkan.VK10.vkCreateImageView;
import static org.lwjgl.vulkan.VK10.vkCreateInstance;
import static org.lwjgl.vulkan.VK10.vkCreatePipelineLayout;
import static org.lwjgl.vulkan.VK10.vkCreateRenderPass;
import static org.lwjgl.vulkan.VK10.vkCreateSampler;
import static org.lwjgl.vulkan.VK10.vkCreateSemaphore;
import static org.lwjgl.vulkan.VK10.vkCreateShaderModule;
import static org.lwjgl.vulkan.VK10.vkDestroyBuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyCommandPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyDevice;
import static org.lwjgl.vulkan.VK10.vkDestroyFence;
import static org.lwjgl.vulkan.VK10.vkDestroyFramebuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyImage;
import static org.lwjgl.vulkan.VK10.vkDestroyImageView;
import static org.lwjgl.vulkan.VK10.vkDestroyInstance;
import static org.lwjgl.vulkan.VK10.vkDestroyPipeline;
import static org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyRenderPass;
import static org.lwjgl.vulkan.VK10.vkDestroySampler;
import static org.lwjgl.vulkan.VK10.vkDestroySemaphore;
import static org.lwjgl.vulkan.VK10.vkDestroyShaderModule;
import static org.lwjgl.vulkan.VK10.vkDeviceWaitIdle;
import static org.lwjgl.vulkan.VK10.vkEndCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkEnumerateDeviceExtensionProperties;
import static org.lwjgl.vulkan.VK10.vkEnumeratePhysicalDevices;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;
import static org.lwjgl.vulkan.VK10.vkGetDeviceQueue;
import static org.lwjgl.vulkan.VK10.vkGetBufferMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceFormatProperties;
import static org.lwjgl.vulkan.VK10.vkGetImageMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceProperties;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceQueueFamilyProperties;
import static org.lwjgl.vulkan.VK10.vkMapMemory;
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;
import static org.lwjgl.vulkan.VK10.vkResetCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkResetFences;
import static org.lwjgl.vulkan.VK10.vkResetCommandPool;
import static org.lwjgl.vulkan.VK10.vkUnmapMemory;
import static org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkWaitForFences;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compile_into_spv;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_initialize;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_release;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compilation_status_success;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_initialize;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_release;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_glsl_fragment_shader;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_glsl_vertex_shader;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_bytes;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_compilation_status;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_error_message;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_release;

/**
 * 中文说明：Vulkan 运行时：负责 GLFW/Vulkan 生命周期与 Vulkan 主循环（通过 Vulkan 呈现软件渲染帧）。
 */
public final class VulkanClientRuntime implements AutoCloseable {
    private static final int TARGET_FPS = 60;
    private static final double MIN_FRAME_SECONDS = 1.0 / TARGET_FPS;
    private static final long GPU_SYNC_TIMEOUT_NANOS = 1_000_000_000L;
    private static final long GPU_SYNC_TIMEOUT_LOG_THROTTLE_NANOS = 1_000_000_000L;
    private static final boolean FRAME_STALL_WATCHDOG_ENABLED = booleanPropertyCompat(
        "vc.vulkan.frameStallWatchdog",
        "voxelcraft.vulkan.frameStallWatchdog",
        true
    );
    private static final long FRAME_STALL_THRESHOLD_NANOS = 3_000_000_000L;
    private static final long FRAME_STALL_LOG_THROTTLE_NANOS = 5_000_000_000L;
    private static final long FRAME_STALL_POLL_MILLIS = 250L;
    private static final long FRAME_STALL_ABORT_NANOS = 10_000_000_000L;
    private static final boolean IS_MAC_OS = System.getProperty("os.name", "").toLowerCase().contains("mac");
    private static final boolean GLFW_CHECK_THREAD0 = booleanPropertyCompat(
        "vc.vulkan.glfwCheckThread0",
        "voxelcraft.vulkan.glfwCheckThread0",
        true
    );
    private static final String VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME = "VK_KHR_portability_enumeration";
    private static final String VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME = "VK_KHR_get_physical_device_properties2";
    private static final String VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME = "VK_KHR_portability_subset";
    private static final int VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR = 0x00000001;
    private static final int SOFTWARE_IMAGE_FORMAT = VK_FORMAT_B8G8R8A8_UNORM;
    private static final boolean CURSOR_CAPTURE_ENABLED = booleanPropertyCompat(
        "vc.vulkan.cursorCapture",
        "voxelcraft.vulkan.cursorCapture",
        !IS_MAC_OS
    );
    private static final boolean VSYNC_ENABLED = booleanPropertyCompat("vc.vsync", "voxelcraft.vsync", true);
    private static final int SOFTWARE_MAX_FRAME_WIDTH = intPropertyCompat(
        "vc.vulkan.softwareMaxWidth",
        "voxelcraft.vulkan.softwareMaxWidth",
        1280
    );
    private static final int SOFTWARE_MAX_FRAME_HEIGHT = intPropertyCompat(
        "vc.vulkan.softwareMaxHeight",
        "voxelcraft.vulkan.softwareMaxHeight",
        720
    );
    private static final boolean PROJECTED_WORLD_ENABLED = booleanPropertyCompat(
        "vc.vulkan.projectedWorld",
        "voxelcraft.vulkan.projectedWorld",
        false
    );
    private static final float PROJECTED_WORLD_VERTICAL_FOV_DEGREES = 75.0f;
    private static final double PROJECTED_WORLD_NEAR_PLANE = 0.05;
    private static final double PROJECTED_WORLD_FAR_PLANE = 256.0;
    private static final int PROJECTED_VERTEX_FLOATS = 7; // x,y,z,r,g,b,a
    private static final int PROJECTED_VERTEX_STRIDE_BYTES = PROJECTED_VERTEX_FLOATS * Float.BYTES;
    private static final java.util.Comparator<ProjectedTranslucentFace> PROJECTED_TRANSLUCENT_FACE_DEPTH_DESC =
        java.util.Comparator.comparingDouble(ProjectedTranslucentFace::sortDepth).reversed();
    private static final String FRAME_VERTEX_SHADER_SOURCE = """
        #version 450

        layout(location = 0) out vec2 vUv;

        vec2 positions[3] = vec2[](
            vec2(-1.0, -1.0),
            vec2( 3.0, -1.0),
            vec2(-1.0,  3.0)
        );

        vec2 uvs[3] = vec2[](
            vec2(0.0, 0.0),
            vec2(2.0, 0.0),
            vec2(0.0, 2.0)
        );

        void main() {
            gl_Position = vec4(positions[gl_VertexIndex], 0.0, 1.0);
            vUv = uvs[gl_VertexIndex];
        }
        """;
    private static final String FRAME_FRAGMENT_SHADER_SOURCE = """
        #version 450

        layout(set = 0, binding = 0) uniform sampler2D uFrame;
        layout(location = 0) in vec2 vUv;
        layout(location = 0) out vec4 outColor;

        void main() {
            outColor = texture(uFrame, vUv);
        }
        """;
    private static final String PROJECTED_WORLD_VERTEX_SHADER_SOURCE = """
        #version 450

        layout(location = 0) in vec3 inPosition;
        layout(location = 1) in vec4 inColor;
        layout(location = 0) out vec4 vColor;

        void main() {
            gl_Position = vec4(inPosition, 1.0);
            vColor = inColor;
        }
        """;
    private static final String PROJECTED_WORLD_FRAGMENT_SHADER_SOURCE = """
        #version 450

        layout(location = 0) in vec4 vColor;
        layout(location = 0) out vec4 outColor;

        void main() {
            outColor = vColor;
        }
        """;

    private final String title;
    private final InputState input = new InputState();

    private long windowHandle = NULL;
    private boolean initialized;
    private boolean firstMouseSample = true;
    private boolean cursorCaptured = true;
    private boolean framebufferResized;

    private VkInstance instance;
    private long surface = VK_NULL_HANDLE;
    private VkPhysicalDevice physicalDevice;
    private VkDevice device;
    private VkQueue graphicsQueue;
    private VkQueue presentQueue;
    private int graphicsQueueFamily = -1;
    private int presentQueueFamily = -1;

    private long swapchain = VK_NULL_HANDLE;
    private int swapchainImageFormat;
    private int swapchainWidth;
    private int swapchainHeight;
    private long[] swapchainImages = new long[0];
    private long[] swapchainImageViews = new long[0];
    private int depthImageFormat = VK_FORMAT_D32_SFLOAT;
    private long depthImage = VK_NULL_HANDLE;
    private long depthImageMemory = VK_NULL_HANDLE;
    private long depthImageView = VK_NULL_HANDLE;
    private long renderPass = VK_NULL_HANDLE;
    private long pipelineLayout = VK_NULL_HANDLE;
    private long graphicsPipeline = VK_NULL_HANDLE;
    private long projectedTranslucentPipeline = VK_NULL_HANDLE;
    private long overlayPipelineLayout = VK_NULL_HANDLE;
    private long overlayPipeline = VK_NULL_HANDLE;
    private long[] swapchainFramebuffers = new long[0];

    private long commandPool = VK_NULL_HANDLE;
    private VkCommandBuffer[] commandBuffers = new VkCommandBuffer[0];

    private long imageAvailableSemaphore = VK_NULL_HANDLE;
    private long renderFinishedSemaphore = VK_NULL_HANDLE;
    private long inFlightFence = VK_NULL_HANDLE;

    private BufferedImage softwareFrame;
    private int[] softwareArgbPixels = new int[0];
    private int softwarePixelCount;
    private int softwareFrameWidth;
    private int softwareFrameHeight;
    private long softwareUploadBuffer = VK_NULL_HANDLE;
    private long softwareUploadBufferMemory = VK_NULL_HANDLE;
    private ByteBuffer softwareUploadMapped;
    private IntBuffer softwareUploadMappedInts;
    private long softwareImage = VK_NULL_HANDLE;
    private long softwareImageMemory = VK_NULL_HANDLE;
    private long softwareImageView = VK_NULL_HANDLE;
    private long softwareSampler = VK_NULL_HANDLE;
    private boolean softwareImagePrimed;

    private long descriptorSetLayout = VK_NULL_HANDLE;
    private long descriptorPool = VK_NULL_HANDLE;
    private long descriptorSet = VK_NULL_HANDLE;

    private final ChunkMesher projectedMesher = new ChunkMesher();
    private final Frustum projectedFrustum = new Frustum();
    private final Frustum.CameraPoint projectedCameraScratch = new Frustum.CameraPoint();
    private final ProjectedVertex projectedScratchV0 = new ProjectedVertex();
    private final ProjectedVertex projectedScratchV1 = new ProjectedVertex();
    private final ProjectedVertex projectedScratchV2 = new ProjectedVertex();
    private final ProjectedVertex projectedScratchV3 = new ProjectedVertex();
    private float[] projectedVertexScratch = new float[0];
    private int projectedVertexFloatCount;
    private int projectedVertexCount;
    private int projectedOpaqueVertexCount;
    private int projectedTranslucentVertexCount;
    private int projectedTotalFaces;
    private int projectedFrustumFaceCandidates;
    private int projectedDrawnFaces;
    private final java.util.ArrayList<ProjectedTranslucentFace> projectedTranslucentFaces = new java.util.ArrayList<>();
    private final java.util.ArrayList<ProjectedTranslucentFace> projectedTranslucentFacePool = new java.util.ArrayList<>();
    private int projectedTranslucentFacePoolUsed;
    private long projectedVertexBuffer = VK_NULL_HANDLE;
    private long projectedVertexBufferMemory = VK_NULL_HANDLE;
    private ByteBuffer projectedVertexMapped;
    private int projectedVertexBufferBytes;
    private float frameAmbient = 1.0f;
    private long lastGpuSyncTimeoutLogNanos;
    private volatile Thread renderThread;
    private volatile Thread frameStallWatchdogThread;
    private volatile boolean frameStallWatchdogRunning;
    private volatile long lastFrameProgressNanos;
    private volatile long lastFrameStallLogNanos;
    private volatile String frameStage = "init";
    private volatile boolean forceRuntimeAbort;
    private volatile String forceRuntimeAbortReason;

    public VulkanClientRuntime(String title) {
        this.title = title;
    }

    public void run(GameClient gameClient) {
        initialize();
        renderThread = Thread.currentThread();
        lastFrameProgressNanos = System.nanoTime();
        frameStage = "loop-start";
        startFrameStallWatchdog();

        long previousNanos = System.nanoTime();
        long fpsWindowStart = previousNanos;
        int frames = 0;
        int displayedFps = 0;

        try {
            while (!glfwWindowShouldClose(windowHandle)) {
                if (forceRuntimeAbort) {
                    throw new IllegalStateException(
                        forceRuntimeAbortReason == null
                            ? "Vulkan runtime aborted due to repeated frame-loop stalls"
                            : forceRuntimeAbortReason
                    );
                }
                long now = System.nanoTime();
                double deltaSeconds = (now - previousNanos) / 1_000_000_000.0;
                previousNanos = now;
                if (deltaSeconds <= 0.0) {
                    deltaSeconds = MIN_FRAME_SECONDS;
                }
                if (deltaSeconds > 0.05) {
                    deltaSeconds = 0.05;
                }

                frameStage = "poll-events";
                glfwPollEvents();
                if (forceRuntimeAbort) {
                    throw new IllegalStateException(
                        forceRuntimeAbortReason == null
                            ? "Vulkan runtime aborted due to repeated frame-loop stalls"
                            : forceRuntimeAbortReason
                    );
                }
                if (CURSOR_CAPTURE_ENABLED) {
                    boolean shouldCaptureCursor = !gameClient.isAnyUiOpen();
                    if (shouldCaptureCursor != cursorCaptured) {
                        glfwSetInputMode(windowHandle, GLFW_CURSOR, shouldCaptureCursor ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
                        cursorCaptured = shouldCaptureCursor;
                        if (shouldCaptureCursor) {
                            firstMouseSample = true;
                        }
                    }
                }
                boolean hadUiOpen = gameClient.isAnyUiOpen();

                frameStage = "tick";
                gameClient.tick(input, deltaSeconds);
                if (input.wasKeyPressed(KeyEvent.VK_ESCAPE) && !hadUiOpen && !gameClient.isAnyUiOpen()) {
                    glfwSetWindowShouldClose(windowHandle, true);
                }
                frameAmbient = gameClient.ambientLight();
                if (PROJECTED_WORLD_ENABLED) {
                    frameStage = "projected-world";
                    updateProjectedWorldVertices(gameClient);
                    frameStage = "projected-overlay";
                    renderProjectedUiOverlay(gameClient);
                } else {
                    frameStage = "software-render";
                    renderSoftwareFrame(gameClient);
                }
                frameStage = "draw-frame";
                drawFrame();
                frameStage = "input-end";
                input.endFrame();

                frames++;
                if (now - fpsWindowStart >= 1_000_000_000L) {
                    displayedFps = frames;
                    frames = 0;
                    fpsWindowStart = now;
                }
                glfwSetWindowTitle(windowHandle, buildVulkanWindowTitle(gameClient, displayedFps));
                lastFrameProgressNanos = System.nanoTime();
                frameStage = "frame-done";
            }
        } finally {
            frameStage = "loop-stop";
            stopFrameStallWatchdog();
        }
    }

    @Override
    public void close() {
        stopFrameStallWatchdog();
        if (!initialized) {
            return;
        }
        if (device != null) {
            vkDeviceWaitIdle(device);
        }
        cleanupSyncObjects();
        cleanupSwapchainResources();

        if (descriptorSetLayout != VK_NULL_HANDLE && device != null) {
            vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
            descriptorSetLayout = VK_NULL_HANDLE;
        }
        if (commandPool != VK_NULL_HANDLE && device != null) {
            vkDestroyCommandPool(device, commandPool, null);
            commandPool = VK_NULL_HANDLE;
        }
        if (device != null) {
            vkDestroyDevice(device, null);
            device = null;
        }
        if (surface != VK_NULL_HANDLE && instance != null) {
            vkDestroySurfaceKHR(instance, surface, null);
            surface = VK_NULL_HANDLE;
        }
        if (instance != null) {
            vkDestroyInstance(instance, null);
            instance = null;
        }

        if (windowHandle != NULL) {
            glfwDestroyWindow(windowHandle);
            windowHandle = NULL;
        }
        glfwTerminate();
        initialized = false;
    }

    private void initialize() {
        if (initialized) {
            return;
        }

        Configuration.GLFW_CHECK_THREAD0.set(GLFW_CHECK_THREAD0);
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("Failed to initialize GLFW");
        }
        if (!glfwVulkanSupported()) {
            throw new IllegalStateException("Vulkan not supported by GLFW/runtime");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);

        windowHandle = glfwCreateWindow(1280, 720, title + " | Vulkan", NULL, NULL);
        if (windowHandle == NULL) {
            throw new IllegalStateException("Failed to create GLFW window for Vulkan");
        }
        installInputCallbacks();
        glfwShowWindow(windowHandle);
        if (CURSOR_CAPTURE_ENABLED) {
            glfwSetInputMode(windowHandle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
            cursorCaptured = true;
        } else {
            glfwSetInputMode(windowHandle, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
            cursorCaptured = false;
        }

        try {
            createInstance();
            createSurface();
            pickPhysicalDevice();
            createLogicalDevice();
            createCommandPool();
            createDescriptorSetLayout();
            createSwapchainResources();
            createSyncObjects();
            logSelectedDevice();
            initialized = true;
        } catch (RuntimeException runtimeException) {
            close();
            throw runtimeException;
        }
    }

    private String buildVulkanWindowTitle(GameClient gameClient, int fps) {
        if (gameClient.isSettingsOpen()) {
            return title + " | Vulkan | Settings | " + gameClient.settingsSummaryText();
        }
        String rendererLabel = PROJECTED_WORLD_ENABLED ? "Vulkan(Projected)" : "Vulkan";
        StringBuilder out = new StringBuilder(title).append(" | ").append(rendererLabel).append(" FPS ").append(fps);
        if (gameClient.showLocationSetting()) {
            var player = gameClient.playerController();
            out.append(
                String.format(
                    " | WXYZ %d %.2f %.2f %.2f",
                    gameClient.activeSliceW(),
                    player.eyeX(),
                    player.eyeY(),
                    player.eyeZ()
                )
            );
        }
        if (gameClient.showStatsSetting()) {
            out.append(" | cg p/r/i ")
                .append(gameClient.pendingChunkGenerationCount()).append("/")
                .append(gameClient.readyGeneratedChunkCount()).append("/")
                .append(gameClient.chunkGenerationJobsInFlight());
            if (PROJECTED_WORLD_ENABLED) {
                out.append(" | pv ").append(projectedVertexCount);
                out.append(" | po ").append(projectedOpaqueVertexCount);
                out.append(" | pt ").append(projectedTranslucentVertexCount);
                out.append(" | pf ").append(projectedDrawnFaces);
            }
        }
        out.append(" | ").append(gameClient.networkStatusLine());
        return out.toString();
    }

    private void createInstance() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkApplicationInfo appInfo = VkApplicationInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
                .pApplicationName(stack.UTF8("Voxelcraft Vulkan"))
                .applicationVersion(VK_MAKE_VERSION(0, 1, 0))
                .pEngineName(stack.UTF8("Voxelcraft"))
                .engineVersion(VK_MAKE_VERSION(0, 1, 0))
                .apiVersion(VK_API_VERSION_1_0);

            PointerBuffer requiredExtensions = glfwGetRequiredInstanceExtensions();
            if (requiredExtensions == null || requiredExtensions.remaining() == 0) {
                throw new IllegalStateException("Unable to query required Vulkan instance extensions from GLFW");
            }

            IntBuffer instanceExtensionCount = stack.mallocInt(1);
            checkVk(
                VK10.vkEnumerateInstanceExtensionProperties((ByteBuffer) null, instanceExtensionCount, null),
                "vkEnumerateInstanceExtensionProperties(count)"
            );
            VkExtensionProperties.Buffer instanceExtensions = VkExtensionProperties.malloc(instanceExtensionCount.get(0), stack);
            checkVk(
                VK10.vkEnumerateInstanceExtensionProperties((ByteBuffer) null, instanceExtensionCount, instanceExtensions),
                "vkEnumerateInstanceExtensionProperties(list)"
            );

            java.util.HashSet<String> availableInstanceExtensions = new java.util.HashSet<>();
            for (int i = 0; i < instanceExtensions.capacity(); i++) {
                availableInstanceExtensions.add(instanceExtensions.get(i).extensionNameString());
            }

            java.util.ArrayList<String> enabledInstanceExtensions = new java.util.ArrayList<>(requiredExtensions.remaining() + 2);
            for (int i = requiredExtensions.position(); i < requiredExtensions.limit(); i++) {
                enabledInstanceExtensions.add(requiredExtensions.getStringUTF8(i));
            }

            int instanceCreateFlags = 0;
            if (IS_MAC_OS && availableInstanceExtensions.contains(VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME)) {
                if (!enabledInstanceExtensions.contains(VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME)) {
                    enabledInstanceExtensions.add(VK_KHR_PORTABILITY_ENUMERATION_EXTENSION_NAME);
                }
                instanceCreateFlags |= VK_INSTANCE_CREATE_ENUMERATE_PORTABILITY_BIT_KHR;
            }
            if (IS_MAC_OS && availableInstanceExtensions.contains(VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME)
                && !enabledInstanceExtensions.contains(VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME)) {
                enabledInstanceExtensions.add(VK_KHR_GET_PHYSICAL_DEVICE_PROPERTIES_2_EXTENSION_NAME);
            }

            PointerBuffer enabledExtensions = stack.mallocPointer(enabledInstanceExtensions.size());
            for (String extensionName : enabledInstanceExtensions) {
                enabledExtensions.put(stack.UTF8(extensionName));
            }
            enabledExtensions.flip();

            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
                .pApplicationInfo(appInfo)
                .flags(instanceCreateFlags)
                .ppEnabledExtensionNames(enabledExtensions);

            PointerBuffer pInstance = stack.mallocPointer(1);
            int result = vkCreateInstance(createInfo, null, pInstance);
            checkVk(result, "vkCreateInstance");
            instance = new VkInstance(pInstance.get(0), createInfo);
        }
    }

    private void createSurface() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pSurface = stack.mallocLong(1);
            int result = glfwCreateWindowSurface(instance, windowHandle, null, pSurface);
            checkVk(result, "glfwCreateWindowSurface");
            surface = pSurface.get(0);
        }
    }

    private void pickPhysicalDevice() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.mallocInt(1);
            checkVk(vkEnumeratePhysicalDevices(instance, count, null), "vkEnumeratePhysicalDevices(count)");
            if (count.get(0) == 0) {
                throw new IllegalStateException("No Vulkan physical device available");
            }
            PointerBuffer devices = stack.mallocPointer(count.get(0));
            checkVk(vkEnumeratePhysicalDevices(instance, count, devices), "vkEnumeratePhysicalDevices(list)");

            for (int i = 0; i < devices.capacity(); i++) {
                VkPhysicalDevice candidate = new VkPhysicalDevice(devices.get(i), instance);
                QueueFamilyIndices indices = findQueueFamilies(candidate);
                if (!indices.complete()) {
                    continue;
                }
                if (!deviceSupportsSwapchain(candidate)) {
                    continue;
                }
                if (!hasSurfaceSupport(candidate)) {
                    continue;
                }
                physicalDevice = candidate;
                graphicsQueueFamily = indices.graphicsFamily;
                presentQueueFamily = indices.presentFamily;
                return;
            }
        }
        throw new IllegalStateException("No suitable Vulkan device (graphics+present+swapchain) found");
    }

    private void createLogicalDevice() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int queueFamilyCount = graphicsQueueFamily == presentQueueFamily ? 1 : 2;
            VkDeviceQueueCreateInfo.Buffer queueCreateInfos = VkDeviceQueueCreateInfo.calloc(queueFamilyCount, stack);

            queueCreateInfos.get(0)
                .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                .queueFamilyIndex(graphicsQueueFamily)
                .pQueuePriorities(stack.floats(1.0f));
            if (queueFamilyCount == 2) {
                queueCreateInfos.get(1)
                    .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
                    .queueFamilyIndex(presentQueueFamily)
                    .pQueuePriorities(stack.floats(1.0f));
            }
            queueCreateInfos.position(0);

            boolean supportsPortabilitySubset = deviceSupportsExtension(physicalDevice, VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME);
            PointerBuffer deviceExtensions = stack.mallocPointer(supportsPortabilitySubset ? 2 : 1);
            deviceExtensions.put(stack.UTF8(VK_KHR_SWAPCHAIN_EXTENSION_NAME));
            if (supportsPortabilitySubset) {
                deviceExtensions.put(stack.UTF8(VK_KHR_PORTABILITY_SUBSET_EXTENSION_NAME));
            }
            deviceExtensions.flip();

            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
                .pQueueCreateInfos(queueCreateInfos)
                .pEnabledFeatures(VkPhysicalDeviceFeatures.calloc(stack))
                .ppEnabledExtensionNames(deviceExtensions);

            PointerBuffer pDevice = stack.mallocPointer(1);
            checkVk(vkCreateDevice(physicalDevice, createInfo, null, pDevice), "vkCreateDevice");
            device = new VkDevice(pDevice.get(0), physicalDevice, createInfo);

            PointerBuffer pQueue = stack.mallocPointer(1);
            vkGetDeviceQueue(device, graphicsQueueFamily, 0, pQueue);
            graphicsQueue = new VkQueue(pQueue.get(0), device);
            vkGetDeviceQueue(device, presentQueueFamily, 0, pQueue);
            presentQueue = new VkQueue(pQueue.get(0), device);
        }
    }

    private void createCommandPool() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandPoolCreateInfo createInfo = VkCommandPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
                .queueFamilyIndex(graphicsQueueFamily)
                .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
            LongBuffer pCommandPool = stack.mallocLong(1);
            checkVk(vkCreateCommandPool(device, createInfo, null, pCommandPool), "vkCreateCommandPool");
            commandPool = pCommandPool.get(0);
        }
    }

    private void createDescriptorSetLayout() {
        if (descriptorSetLayout != VK_NULL_HANDLE) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
            bindings.get(0)
                .binding(0)
                .descriptorCount(1)
                .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .pImmutableSamplers(null)
                .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);

            VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                .pBindings(bindings);

            LongBuffer pLayout = stack.mallocLong(1);
            checkVk(vkCreateDescriptorSetLayout(device, layoutInfo, null, pLayout), "vkCreateDescriptorSetLayout");
            descriptorSetLayout = pLayout.get(0);
        }
    }

    private void createSwapchainResources() {
        createSwapchain();
        createImageViews();
        if (PROJECTED_WORLD_ENABLED) {
            createDepthResources();
            createProjectedWorldResources();
        }
        createSoftwareFrameResources();
        createRenderPass();
        createGraphicsPipeline();
        if (PROJECTED_WORLD_ENABLED) {
            createProjectedTranslucentPipeline();
            createOverlayPipeline();
        }
        createFramebuffers();
        allocateCommandBuffers();
        framebufferResized = false;
    }

    private void recreateSwapchainResources() {
        if (!waitForNonZeroFramebuffer()) {
            return;
        }
        vkDeviceWaitIdle(device);
        cleanupSwapchainResources();
        createSwapchainResources();
    }

    private void cleanupSwapchainResources() {
        commandBuffers = new VkCommandBuffer[0];
        if (commandPool != VK_NULL_HANDLE && device != null) {
            vkResetCommandPool(device, commandPool, 0);
        }

        for (long framebuffer : swapchainFramebuffers) {
            if (framebuffer != VK_NULL_HANDLE) {
                vkDestroyFramebuffer(device, framebuffer, null);
            }
        }
        swapchainFramebuffers = new long[0];

        if (graphicsPipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(device, graphicsPipeline, null);
            graphicsPipeline = VK_NULL_HANDLE;
        }
        if (projectedTranslucentPipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(device, projectedTranslucentPipeline, null);
            projectedTranslucentPipeline = VK_NULL_HANDLE;
        }
        if (overlayPipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(device, overlayPipeline, null);
            overlayPipeline = VK_NULL_HANDLE;
        }
        if (pipelineLayout != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(device, pipelineLayout, null);
            pipelineLayout = VK_NULL_HANDLE;
        }
        if (overlayPipelineLayout != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(device, overlayPipelineLayout, null);
            overlayPipelineLayout = VK_NULL_HANDLE;
        }

        if (PROJECTED_WORLD_ENABLED) {
            cleanupProjectedWorldResources();
        }
        cleanupSoftwareFrameResources();

        cleanupDepthResources();

        if (renderPass != VK_NULL_HANDLE) {
            vkDestroyRenderPass(device, renderPass, null);
            renderPass = VK_NULL_HANDLE;
        }

        for (long imageView : swapchainImageViews) {
            if (imageView != VK_NULL_HANDLE) {
                vkDestroyImageView(device, imageView, null);
            }
        }
        swapchainImageViews = new long[0];
        swapchainImages = new long[0];

        if (swapchain != VK_NULL_HANDLE) {
            vkDestroySwapchainKHR(device, swapchain, null);
            swapchain = VK_NULL_HANDLE;
        }
    }

    private void cleanupSyncObjects() {
        if (imageAvailableSemaphore != VK_NULL_HANDLE) {
            vkDestroySemaphore(device, imageAvailableSemaphore, null);
            imageAvailableSemaphore = VK_NULL_HANDLE;
        }
        if (renderFinishedSemaphore != VK_NULL_HANDLE) {
            vkDestroySemaphore(device, renderFinishedSemaphore, null);
            renderFinishedSemaphore = VK_NULL_HANDLE;
        }
        if (inFlightFence != VK_NULL_HANDLE) {
            vkDestroyFence(device, inFlightFence, null);
            inFlightFence = VK_NULL_HANDLE;
        }
    }

    private void createSwapchain() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.malloc(stack);
            checkVk(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, capabilities), "vkGetPhysicalDeviceSurfaceCapabilitiesKHR");

            IntBuffer formatCount = stack.mallocInt(1);
            checkVk(vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCount, null), "vkGetPhysicalDeviceSurfaceFormatsKHR(count)");
            if (formatCount.get(0) <= 0) {
                throw new IllegalStateException("No Vulkan surface formats available");
            }
            VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.malloc(formatCount.get(0), stack);
            checkVk(vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, formatCount, formats), "vkGetPhysicalDeviceSurfaceFormatsKHR(list)");
            SurfaceFormatChoice chosenFormat = chooseSurfaceFormat(formats);

            IntBuffer presentModeCount = stack.mallocInt(1);
            checkVk(vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, presentModeCount, null), "vkGetPhysicalDeviceSurfacePresentModesKHR(count)");
            if (presentModeCount.get(0) <= 0) {
                throw new IllegalStateException("No Vulkan present modes available");
            }
            IntBuffer presentModes = stack.mallocInt(presentModeCount.get(0));
            checkVk(vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, presentModeCount, presentModes), "vkGetPhysicalDeviceSurfacePresentModesKHR(list)");
            int chosenPresentMode = choosePresentMode(presentModes);

            ExtentChoice extentChoice = chooseSwapchainExtent(capabilities);
            int imageCount = capabilities.minImageCount() + 1;
            if (capabilities.maxImageCount() > 0 && imageCount > capabilities.maxImageCount()) {
                imageCount = capabilities.maxImageCount();
            }

            VkSwapchainCreateInfoKHR createInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
                .surface(surface)
                .minImageCount(imageCount)
                .imageFormat(chosenFormat.format)
                .imageColorSpace(chosenFormat.colorSpace)
                .imageExtent(e -> e.set(extentChoice.width, extentChoice.height))
                .imageArrayLayers(1)
                .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                .preTransform(capabilities.currentTransform())
                .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                .presentMode(chosenPresentMode)
                .clipped(true)
                .oldSwapchain(VK_NULL_HANDLE);

            if (graphicsQueueFamily != presentQueueFamily) {
                createInfo.imageSharingMode(VK_SHARING_MODE_CONCURRENT);
                createInfo.pQueueFamilyIndices(stack.ints(graphicsQueueFamily, presentQueueFamily));
            } else {
                createInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE);
            }

            LongBuffer pSwapchain = stack.mallocLong(1);
            checkVk(vkCreateSwapchainKHR(device, createInfo, null, pSwapchain), "vkCreateSwapchainKHR");
            swapchain = pSwapchain.get(0);
            swapchainImageFormat = chosenFormat.format;
            swapchainWidth = extentChoice.width;
            swapchainHeight = extentChoice.height;

            IntBuffer pImageCount = stack.mallocInt(1);
            checkVk(vkGetSwapchainImagesKHR(device, swapchain, pImageCount, null), "vkGetSwapchainImagesKHR(count)");
            LongBuffer imageBuffer = stack.mallocLong(pImageCount.get(0));
            checkVk(vkGetSwapchainImagesKHR(device, swapchain, pImageCount, imageBuffer), "vkGetSwapchainImagesKHR(list)");
            swapchainImages = new long[imageBuffer.remaining()];
            for (int i = 0; i < swapchainImages.length; i++) {
                swapchainImages[i] = imageBuffer.get(i);
            }
        }
    }

    private void createImageViews() {
        swapchainImageViews = new long[swapchainImages.length];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pImageView = stack.mallocLong(1);
            for (int i = 0; i < swapchainImages.length; i++) {
                VkImageViewCreateInfo createInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .image(swapchainImages[i])
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(swapchainImageFormat);
                createInfo.components()
                    .r(VK_COMPONENT_SWIZZLE_IDENTITY)
                    .g(VK_COMPONENT_SWIZZLE_IDENTITY)
                    .b(VK_COMPONENT_SWIZZLE_IDENTITY)
                    .a(VK_COMPONENT_SWIZZLE_IDENTITY);
                createInfo.subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);

                checkVk(vkCreateImageView(device, createInfo, null, pImageView), "vkCreateImageView");
                swapchainImageViews[i] = pImageView.get(0);
            }
        }
    }

    private void createDepthResources() {
        depthImageFormat = findSupportedDepthFormat();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                .imageType(VK_IMAGE_TYPE_2D)
                .format(depthImageFormat)
                .mipLevels(1)
                .arrayLayers(1)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .tiling(VK_IMAGE_TILING_OPTIMAL)
                .usage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            imageInfo.extent()
                .width(swapchainWidth)
                .height(swapchainHeight)
                .depth(1);

            LongBuffer pImage = stack.mallocLong(1);
            checkVk(vkCreateImage(device, imageInfo, null, pImage), "vkCreateImage(depth)");
            depthImage = pImage.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
            vkGetImageMemoryRequirements(device, depthImage, requirements);

            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(requirements.size())
                .memoryTypeIndex(findMemoryType(requirements.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));

            LongBuffer pMemory = stack.mallocLong(1);
            checkVk(vkAllocateMemory(device, allocInfo, null, pMemory), "vkAllocateMemory(depth)");
            depthImageMemory = pMemory.get(0);
            checkVk(vkBindImageMemory(device, depthImage, depthImageMemory, 0L), "vkBindImageMemory(depth)");

            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                .image(depthImage)
                .viewType(VK_IMAGE_VIEW_TYPE_2D)
                .format(depthImageFormat);
            int aspectMask = hasStencilComponent(depthImageFormat) ? (VK_IMAGE_ASPECT_DEPTH_BIT | VK_IMAGE_ASPECT_STENCIL_BIT) : VK_IMAGE_ASPECT_DEPTH_BIT;
            viewInfo.subresourceRange()
                .aspectMask(aspectMask)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);
            viewInfo.components()
                .r(VK_COMPONENT_SWIZZLE_IDENTITY)
                .g(VK_COMPONENT_SWIZZLE_IDENTITY)
                .b(VK_COMPONENT_SWIZZLE_IDENTITY)
                .a(VK_COMPONENT_SWIZZLE_IDENTITY);

            LongBuffer pView = stack.mallocLong(1);
            checkVk(vkCreateImageView(device, viewInfo, null, pView), "vkCreateImageView(depth)");
            depthImageView = pView.get(0);
        }
    }

    private void cleanupDepthResources() {
        if (depthImageView != VK_NULL_HANDLE) {
            vkDestroyImageView(device, depthImageView, null);
            depthImageView = VK_NULL_HANDLE;
        }
        if (depthImage != VK_NULL_HANDLE) {
            vkDestroyImage(device, depthImage, null);
            depthImage = VK_NULL_HANDLE;
        }
        if (depthImageMemory != VK_NULL_HANDLE) {
            vkFreeMemory(device, depthImageMemory, null);
            depthImageMemory = VK_NULL_HANDLE;
        }
    }

    private int findSupportedDepthFormat() {
        int[] candidates = new int[] {VK_FORMAT_D32_SFLOAT, VK_FORMAT_D32_SFLOAT_S8_UINT, VK_FORMAT_D24_UNORM_S8_UINT};
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFormatProperties properties = VkFormatProperties.malloc(stack);
            for (int candidate : candidates) {
                vkGetPhysicalDeviceFormatProperties(physicalDevice, candidate, properties);
                if ((properties.optimalTilingFeatures() & VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) != 0) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("No Vulkan depth format supported for depth-stencil attachment");
    }

    private static boolean hasStencilComponent(int format) {
        return format == VK_FORMAT_D32_SFLOAT_S8_UINT || format == VK_FORMAT_D24_UNORM_S8_UINT;
    }

    private void createRenderPass() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int attachmentCount = PROJECTED_WORLD_ENABLED ? 2 : 1;
            VkAttachmentDescription.Buffer attachments = VkAttachmentDescription.calloc(attachmentCount, stack);
            attachments.get(0)
                .format(swapchainImageFormat)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
            if (PROJECTED_WORLD_ENABLED) {
                attachments.get(1)
                    .format(depthImageFormat)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
                    .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
            }

            VkAttachmentReference.Buffer colorAttachmentRef = VkAttachmentReference.calloc(1, stack);
            colorAttachmentRef.get(0)
                .attachment(0)
                .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            VkAttachmentReference.Buffer depthAttachmentRef = null;
            if (PROJECTED_WORLD_ENABLED) {
                depthAttachmentRef = VkAttachmentReference.calloc(1, stack);
                depthAttachmentRef.get(0)
                    .attachment(1)
                    .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
            }

            VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack);
            subpass.get(0)
                .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                .colorAttachmentCount(1)
                .pColorAttachments(colorAttachmentRef);
            if (PROJECTED_WORLD_ENABLED) {
                subpass.get(0).pDepthStencilAttachment(depthAttachmentRef.get(0));
            }

            VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(1, stack);
            dependency.get(0)
                .srcSubpass(VK_SUBPASS_EXTERNAL)
                .dstSubpass(0)
                .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | (PROJECTED_WORLD_ENABLED ? VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT : 0))
                .srcAccessMask(0)
                .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | (PROJECTED_WORLD_ENABLED ? VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT : 0))
                .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | (PROJECTED_WORLD_ENABLED ? VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT : 0));

            VkRenderPassCreateInfo renderPassInfo = VkRenderPassCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO)
                .pAttachments(attachments)
                .pSubpasses(subpass)
                .pDependencies(dependency);

            LongBuffer pRenderPass = stack.mallocLong(1);
            checkVk(vkCreateRenderPass(device, renderPassInfo, null, pRenderPass), "vkCreateRenderPass");
            renderPass = pRenderPass.get(0);
        }
    }

    private void createGraphicsPipeline() {
        long vertexShaderModule = createShaderModuleFromSource(
            PROJECTED_WORLD_ENABLED ? PROJECTED_WORLD_VERTEX_SHADER_SOURCE : FRAME_VERTEX_SHADER_SOURCE,
            shaderc_glsl_vertex_shader,
            PROJECTED_WORLD_ENABLED ? "projected_world.vert" : "frame.vert"
        );
        long fragmentShaderModule = createShaderModuleFromSource(
            PROJECTED_WORLD_ENABLED ? PROJECTED_WORLD_FRAGMENT_SHADER_SOURCE : FRAME_FRAGMENT_SHADER_SOURCE,
            shaderc_glsl_fragment_shader,
            PROJECTED_WORLD_ENABLED ? "projected_world.frag" : "frame.frag"
        );

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            shaderStages.get(0)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                .stage(VK_SHADER_STAGE_VERTEX_BIT)
                .module(vertexShaderModule)
                .pName(stack.UTF8("main"));
            shaderStages.get(1)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                .module(fragmentShaderModule)
                .pName(stack.UTF8("main"));

            VkPipelineVertexInputStateCreateInfo vertexInputInfo = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);
            if (PROJECTED_WORLD_ENABLED) {
                VkVertexInputBindingDescription.Buffer bindingDescription = VkVertexInputBindingDescription.calloc(1, stack);
                bindingDescription.get(0)
                    .binding(0)
                    .stride(PROJECTED_VERTEX_STRIDE_BYTES)
                    .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

                VkVertexInputAttributeDescription.Buffer attributeDescriptions = VkVertexInputAttributeDescription.calloc(2, stack);
                attributeDescriptions.get(0)
                    .binding(0)
                    .location(0)
                    .format(VK_FORMAT_R32G32B32_SFLOAT)
                    .offset(0);
                attributeDescriptions.get(1)
                    .binding(0)
                    .location(1)
                    .format(VK_FORMAT_R32G32B32A32_SFLOAT)
                    .offset(3 * Float.BYTES);

                vertexInputInfo
                    .pVertexBindingDescriptions(bindingDescription)
                    .pVertexAttributeDescriptions(attributeDescriptions);
            }

            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                .primitiveRestartEnable(false);

            VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
            viewport.get(0)
                .x(0.0f)
                .y(0.0f)
                .width((float) swapchainWidth)
                .height((float) swapchainHeight)
                .minDepth(0.0f)
                .maxDepth(1.0f);

            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
            scissor.get(0).offset().set(0, 0);
            scissor.get(0).extent().set(swapchainWidth, swapchainHeight);

            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                .viewportCount(1)
                .pViewports(viewport)
                .scissorCount(1)
                .pScissors(scissor);

            VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                .depthClampEnable(false)
                .rasterizerDiscardEnable(false)
                .polygonMode(VK_POLYGON_MODE_FILL)
                .lineWidth(1.0f)
                .cullMode(VK_CULL_MODE_NONE)
                .frontFace(VK_FRONT_FACE_CLOCKWISE)
                .depthBiasEnable(false);

            VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                .sampleShadingEnable(false)
                .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);
            VkPipelineDepthStencilStateCreateInfo depthStencil = null;
            if (PROJECTED_WORLD_ENABLED) {
                depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                    .depthTestEnable(true)
                    .depthWriteEnable(true)
                    .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL)
                    .depthBoundsTestEnable(false)
                    .stencilTestEnable(false);
            }

            VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack);
            colorBlendAttachment.get(0)
                .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
                .blendEnable(false);

            VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                .logicOpEnable(false)
                .logicOp(VK_LOGIC_OP_COPY)
                .pAttachments(colorBlendAttachment)
                .blendConstants(stack.floats(0.0f, 0.0f, 0.0f, 0.0f));

            VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO);
            if (!PROJECTED_WORLD_ENABLED) {
                pipelineLayoutInfo.pSetLayouts(stack.longs(descriptorSetLayout));
            }

            LongBuffer pPipelineLayout = stack.mallocLong(1);
            checkVk(vkCreatePipelineLayout(device, pipelineLayoutInfo, null, pPipelineLayout), "vkCreatePipelineLayout");
            pipelineLayout = pPipelineLayout.get(0);

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack);
            pipelineInfo.get(0)
                .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                .pStages(shaderStages)
                .pVertexInputState(vertexInputInfo)
                .pInputAssemblyState(inputAssembly)
                .pViewportState(viewportState)
                .pRasterizationState(rasterizer)
                .pMultisampleState(multisampling)
                .pColorBlendState(colorBlending)
                .layout(pipelineLayout)
                .renderPass(renderPass)
                .subpass(0)
                .basePipelineHandle(VK_NULL_HANDLE)
                .basePipelineIndex(-1);
            if (PROJECTED_WORLD_ENABLED) {
                pipelineInfo.get(0).pDepthStencilState(depthStencil);
            }

            LongBuffer pGraphicsPipeline = stack.mallocLong(1);
            int createResult = vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pGraphicsPipeline);
            if (createResult != VK_SUCCESS) {
                if (pipelineLayout != VK_NULL_HANDLE) {
                    vkDestroyPipelineLayout(device, pipelineLayout, null);
                    pipelineLayout = VK_NULL_HANDLE;
                }
                throw new IllegalStateException("vkCreateGraphicsPipelines failed with Vulkan error code " + createResult);
            }
            graphicsPipeline = pGraphicsPipeline.get(0);
        } finally {
            if (vertexShaderModule != VK_NULL_HANDLE) {
                vkDestroyShaderModule(device, vertexShaderModule, null);
            }
            if (fragmentShaderModule != VK_NULL_HANDLE) {
                vkDestroyShaderModule(device, fragmentShaderModule, null);
            }
        }
    }

    private void createProjectedTranslucentPipeline() {
        if (pipelineLayout == VK_NULL_HANDLE) {
            throw new IllegalStateException("Projected translucent pipeline requires primary projected pipeline layout");
        }
        long vertexShaderModule = createShaderModuleFromSource(
            PROJECTED_WORLD_VERTEX_SHADER_SOURCE,
            shaderc_glsl_vertex_shader,
            "projected_world_translucent.vert"
        );
        long fragmentShaderModule = createShaderModuleFromSource(
            PROJECTED_WORLD_FRAGMENT_SHADER_SOURCE,
            shaderc_glsl_fragment_shader,
            "projected_world_translucent.frag"
        );

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            shaderStages.get(0)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                .stage(VK_SHADER_STAGE_VERTEX_BIT)
                .module(vertexShaderModule)
                .pName(stack.UTF8("main"));
            shaderStages.get(1)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                .module(fragmentShaderModule)
                .pName(stack.UTF8("main"));

            VkVertexInputBindingDescription.Buffer bindingDescription = VkVertexInputBindingDescription.calloc(1, stack);
            bindingDescription.get(0)
                .binding(0)
                .stride(PROJECTED_VERTEX_STRIDE_BYTES)
                .inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

            VkVertexInputAttributeDescription.Buffer attributeDescriptions = VkVertexInputAttributeDescription.calloc(2, stack);
            attributeDescriptions.get(0)
                .binding(0)
                .location(0)
                .format(VK_FORMAT_R32G32B32_SFLOAT)
                .offset(0);
            attributeDescriptions.get(1)
                .binding(0)
                .location(1)
                .format(VK_FORMAT_R32G32B32A32_SFLOAT)
                .offset(3 * Float.BYTES);

            VkPipelineVertexInputStateCreateInfo vertexInputInfo = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                .pVertexBindingDescriptions(bindingDescription)
                .pVertexAttributeDescriptions(attributeDescriptions);

            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                .primitiveRestartEnable(false);

            VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
            viewport.get(0)
                .x(0.0f)
                .y(0.0f)
                .width((float) swapchainWidth)
                .height((float) swapchainHeight)
                .minDepth(0.0f)
                .maxDepth(1.0f);

            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
            scissor.get(0).offset().set(0, 0);
            scissor.get(0).extent().set(swapchainWidth, swapchainHeight);

            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                .viewportCount(1)
                .pViewports(viewport)
                .scissorCount(1)
                .pScissors(scissor);

            VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                .depthClampEnable(false)
                .rasterizerDiscardEnable(false)
                .polygonMode(VK_POLYGON_MODE_FILL)
                .lineWidth(1.0f)
                .cullMode(VK_CULL_MODE_NONE)
                .frontFace(VK_FRONT_FACE_CLOCKWISE)
                .depthBiasEnable(false);

            VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                .sampleShadingEnable(false)
                .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

            VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                .depthTestEnable(true)
                .depthWriteEnable(false)
                .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL)
                .depthBoundsTestEnable(false)
                .stencilTestEnable(false);

            VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack);
            colorBlendAttachment.get(0)
                .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
                .blendEnable(true)
                .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                .colorBlendOp(VK_BLEND_OP_ADD)
                .srcAlphaBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                .dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                .alphaBlendOp(VK_BLEND_OP_ADD);

            VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                .logicOpEnable(false)
                .logicOp(VK_LOGIC_OP_COPY)
                .pAttachments(colorBlendAttachment)
                .blendConstants(stack.floats(0.0f, 0.0f, 0.0f, 0.0f));

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack);
            pipelineInfo.get(0)
                .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                .pStages(shaderStages)
                .pVertexInputState(vertexInputInfo)
                .pInputAssemblyState(inputAssembly)
                .pViewportState(viewportState)
                .pRasterizationState(rasterizer)
                .pMultisampleState(multisampling)
                .pDepthStencilState(depthStencil)
                .pColorBlendState(colorBlending)
                .layout(pipelineLayout)
                .renderPass(renderPass)
                .subpass(0)
                .basePipelineHandle(VK_NULL_HANDLE)
                .basePipelineIndex(-1);

            LongBuffer pPipeline = stack.mallocLong(1);
            int createResult = vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pPipeline);
            if (createResult != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateGraphicsPipelines(projected translucent) failed with Vulkan error code " + createResult);
            }
            projectedTranslucentPipeline = pPipeline.get(0);
        } finally {
            if (vertexShaderModule != VK_NULL_HANDLE) {
                vkDestroyShaderModule(device, vertexShaderModule, null);
            }
            if (fragmentShaderModule != VK_NULL_HANDLE) {
                vkDestroyShaderModule(device, fragmentShaderModule, null);
            }
        }
    }

    private void createOverlayPipeline() {
        long vertexShaderModule = createShaderModuleFromSource(
            FRAME_VERTEX_SHADER_SOURCE,
            shaderc_glsl_vertex_shader,
            "overlay_frame.vert"
        );
        long fragmentShaderModule = createShaderModuleFromSource(
            FRAME_FRAGMENT_SHADER_SOURCE,
            shaderc_glsl_fragment_shader,
            "overlay_frame.frag"
        );

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            shaderStages.get(0)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                .stage(VK_SHADER_STAGE_VERTEX_BIT)
                .module(vertexShaderModule)
                .pName(stack.UTF8("main"));
            shaderStages.get(1)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                .module(fragmentShaderModule)
                .pName(stack.UTF8("main"));

            VkPipelineVertexInputStateCreateInfo vertexInputInfo = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO);

            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                .primitiveRestartEnable(false);

            VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
            viewport.get(0)
                .x(0.0f)
                .y(0.0f)
                .width((float) swapchainWidth)
                .height((float) swapchainHeight)
                .minDepth(0.0f)
                .maxDepth(1.0f);

            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
            scissor.get(0).offset().set(0, 0);
            scissor.get(0).extent().set(swapchainWidth, swapchainHeight);

            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                .viewportCount(1)
                .pViewports(viewport)
                .scissorCount(1)
                .pScissors(scissor);

            VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                .depthClampEnable(false)
                .rasterizerDiscardEnable(false)
                .polygonMode(VK_POLYGON_MODE_FILL)
                .lineWidth(1.0f)
                .cullMode(VK_CULL_MODE_NONE)
                .frontFace(VK_FRONT_FACE_CLOCKWISE)
                .depthBiasEnable(false);

            VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                .sampleShadingEnable(false)
                .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

            VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                .depthTestEnable(false)
                .depthWriteEnable(false)
                .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL)
                .depthBoundsTestEnable(false)
                .stencilTestEnable(false);

            VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack);
            colorBlendAttachment.get(0)
                .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT)
                .blendEnable(true)
                .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                .colorBlendOp(VK_BLEND_OP_ADD)
                .srcAlphaBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                .dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                .alphaBlendOp(VK_BLEND_OP_ADD);

            VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                .logicOpEnable(false)
                .logicOp(VK_LOGIC_OP_COPY)
                .pAttachments(colorBlendAttachment)
                .blendConstants(stack.floats(0.0f, 0.0f, 0.0f, 0.0f));

            VkPipelineLayoutCreateInfo pipelineLayoutInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                .pSetLayouts(stack.longs(descriptorSetLayout));
            LongBuffer pPipelineLayout = stack.mallocLong(1);
            checkVk(vkCreatePipelineLayout(device, pipelineLayoutInfo, null, pPipelineLayout), "vkCreatePipelineLayout(overlay)");
            overlayPipelineLayout = pPipelineLayout.get(0);

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack);
            pipelineInfo.get(0)
                .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                .pStages(shaderStages)
                .pVertexInputState(vertexInputInfo)
                .pInputAssemblyState(inputAssembly)
                .pViewportState(viewportState)
                .pRasterizationState(rasterizer)
                .pMultisampleState(multisampling)
                .pDepthStencilState(depthStencil)
                .pColorBlendState(colorBlending)
                .layout(overlayPipelineLayout)
                .renderPass(renderPass)
                .subpass(0)
                .basePipelineHandle(VK_NULL_HANDLE)
                .basePipelineIndex(-1);

            LongBuffer pPipeline = stack.mallocLong(1);
            int createResult = vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipelineInfo, null, pPipeline);
            if (createResult != VK_SUCCESS) {
                if (overlayPipelineLayout != VK_NULL_HANDLE) {
                    vkDestroyPipelineLayout(device, overlayPipelineLayout, null);
                    overlayPipelineLayout = VK_NULL_HANDLE;
                }
                throw new IllegalStateException("vkCreateGraphicsPipelines(overlay) failed with Vulkan error code " + createResult);
            }
            overlayPipeline = pPipeline.get(0);
        } finally {
            if (vertexShaderModule != VK_NULL_HANDLE) {
                vkDestroyShaderModule(device, vertexShaderModule, null);
            }
            if (fragmentShaderModule != VK_NULL_HANDLE) {
                vkDestroyShaderModule(device, fragmentShaderModule, null);
            }
        }
    }

    private long createShaderModuleFromSource(String source, int shaderKind, String name) {
        long compiler = shaderc_compiler_initialize();
        if (compiler == NULL) {
            throw new IllegalStateException("Failed to initialize shaderc compiler");
        }
        long options = shaderc_compile_options_initialize();
        if (options == NULL) {
            shaderc_compiler_release(compiler);
            throw new IllegalStateException("Failed to initialize shaderc compile options");
        }
        long result = shaderc_compile_into_spv(compiler, source, shaderKind, name, "main", options);
        if (result == NULL) {
            shaderc_compile_options_release(options);
            shaderc_compiler_release(compiler);
            throw new IllegalStateException("shaderc_compile_into_spv returned NULL for " + name);
        }
        try {
            int compileStatus = shaderc_result_get_compilation_status(result);
            if (compileStatus != shaderc_compilation_status_success) {
                throw new IllegalStateException("Shader compile failed (" + name + "): " + shaderc_result_get_error_message(result));
            }
            ByteBuffer bytecode = shaderc_result_get_bytes(result);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                    .pCode(bytecode);
                LongBuffer pShaderModule = stack.mallocLong(1);
                checkVk(vkCreateShaderModule(device, createInfo, null, pShaderModule), "vkCreateShaderModule(" + name + ")");
                return pShaderModule.get(0);
            }
        } finally {
            shaderc_result_release(result);
            shaderc_compile_options_release(options);
            shaderc_compiler_release(compiler);
        }
    }

    private void createProjectedWorldResources() {
        ensureProjectedVertexBufferCapacityBytes(4 * 1024 * 1024);
    }

    private void cleanupProjectedWorldResources() {
        if (projectedVertexBuffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, projectedVertexBuffer, null);
            projectedVertexBuffer = VK_NULL_HANDLE;
        }
        if (projectedVertexBufferMemory != VK_NULL_HANDLE) {
            if (projectedVertexMapped != null) {
                vkUnmapMemory(device, projectedVertexBufferMemory);
            }
            vkFreeMemory(device, projectedVertexBufferMemory, null);
            projectedVertexBufferMemory = VK_NULL_HANDLE;
        }
        projectedVertexMapped = null;
        projectedVertexBufferBytes = 0;
        projectedVertexFloatCount = 0;
        projectedVertexCount = 0;
        projectedOpaqueVertexCount = 0;
        projectedTranslucentVertexCount = 0;
        projectedTranslucentFaces.clear();
        projectedTranslucentFacePoolUsed = 0;
    }

    private void ensureProjectedVertexBufferCapacityBytes(int requiredBytes) {
        if (requiredBytes <= projectedVertexBufferBytes) {
            return;
        }
        int targetBytes = Math.max(requiredBytes, Math.max(projectedVertexBufferBytes * 2, 4 * 1024 * 1024));
        cleanupProjectedWorldResources();
        createProjectedVertexBuffer(targetBytes);
    }

    private void createProjectedVertexBuffer(int bufferBytes) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                .size(bufferBytes)
                .usage(VK_BUFFER_USAGE_VERTEX_BUFFER_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            LongBuffer pBuffer = stack.mallocLong(1);
            checkVk(vkCreateBuffer(device, bufferInfo, null, pBuffer), "vkCreateBuffer(projectedVertex)");
            projectedVertexBuffer = pBuffer.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
            vkGetBufferMemoryRequirements(device, projectedVertexBuffer, requirements);

            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(requirements.size())
                .memoryTypeIndex(findMemoryType(
                    requirements.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
                ));

            LongBuffer pMemory = stack.mallocLong(1);
            checkVk(vkAllocateMemory(device, allocInfo, null, pMemory), "vkAllocateMemory(projectedVertex)");
            projectedVertexBufferMemory = pMemory.get(0);
            checkVk(vkBindBufferMemory(device, projectedVertexBuffer, projectedVertexBufferMemory, 0L), "vkBindBufferMemory(projectedVertex)");

            PointerBuffer pMapped = stack.mallocPointer(1);
            checkVk(vkMapMemory(device, projectedVertexBufferMemory, 0L, bufferBytes, 0, pMapped), "vkMapMemory(projectedVertex)");
            projectedVertexMapped = memByteBuffer(pMapped.get(0), bufferBytes);
            projectedVertexBufferBytes = bufferBytes;
        }
    }

    private void createSoftwareFrameResources() {
        createSoftwareFrameTarget();
        createSoftwareUploadBuffer();
        createSoftwareImage();
        createSoftwareImageView();
        createSoftwareSampler();
        createDescriptorPoolAndSet();
    }

    private void cleanupSoftwareFrameResources() {
        descriptorSet = VK_NULL_HANDLE;
        if (descriptorPool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(device, descriptorPool, null);
            descriptorPool = VK_NULL_HANDLE;
        }

        if (softwareSampler != VK_NULL_HANDLE) {
            vkDestroySampler(device, softwareSampler, null);
            softwareSampler = VK_NULL_HANDLE;
        }
        if (softwareImageView != VK_NULL_HANDLE) {
            vkDestroyImageView(device, softwareImageView, null);
            softwareImageView = VK_NULL_HANDLE;
        }
        if (softwareImage != VK_NULL_HANDLE) {
            vkDestroyImage(device, softwareImage, null);
            softwareImage = VK_NULL_HANDLE;
        }
        if (softwareImageMemory != VK_NULL_HANDLE) {
            vkFreeMemory(device, softwareImageMemory, null);
            softwareImageMemory = VK_NULL_HANDLE;
        }

        if (softwareUploadBuffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(device, softwareUploadBuffer, null);
            softwareUploadBuffer = VK_NULL_HANDLE;
        }
        if (softwareUploadBufferMemory != VK_NULL_HANDLE) {
            if (softwareUploadMapped != null) {
                vkUnmapMemory(device, softwareUploadBufferMemory);
            }
            vkFreeMemory(device, softwareUploadBufferMemory, null);
            softwareUploadBufferMemory = VK_NULL_HANDLE;
        }

        softwareUploadMapped = null;
        softwareUploadMappedInts = null;
        softwareImagePrimed = false;
        softwareFrame = null;
        softwareArgbPixels = new int[0];
        softwarePixelCount = 0;
        softwareFrameWidth = 0;
        softwareFrameHeight = 0;
    }

    private void createSoftwareFrameTarget() {
        ExtentChoice extent = chooseSoftwareFrameExtent();
        softwareFrameWidth = extent.width;
        softwareFrameHeight = extent.height;
        softwareFrame = new BufferedImage(softwareFrameWidth, softwareFrameHeight, BufferedImage.TYPE_INT_ARGB);
        softwareArgbPixels = ((DataBufferInt) softwareFrame.getRaster().getDataBuffer()).getData();
        softwarePixelCount = softwareArgbPixels.length;
        if (softwareFrameWidth != swapchainWidth || softwareFrameHeight != swapchainHeight) {
            System.out.printf(
                "[vulkan] software frame scaled to %dx%d (swapchain=%dx%d)%n",
                softwareFrameWidth,
                softwareFrameHeight,
                swapchainWidth,
                swapchainHeight
            );
        }
    }

    private ExtentChoice chooseSoftwareFrameExtent() {
        if (swapchainWidth <= 0 || swapchainHeight <= 0) {
            return new ExtentChoice(1, 1);
        }
        int maxWidth = Math.max(1, SOFTWARE_MAX_FRAME_WIDTH);
        int maxHeight = Math.max(1, SOFTWARE_MAX_FRAME_HEIGHT);
        double scaleX = (double) maxWidth / (double) swapchainWidth;
        double scaleY = (double) maxHeight / (double) swapchainHeight;
        double scale = Math.min(1.0, Math.min(scaleX, scaleY));
        int width = Math.max(1, (int) Math.round(swapchainWidth * scale));
        int height = Math.max(1, (int) Math.round(swapchainHeight * scale));
        return new ExtentChoice(width, height);
    }

    private void createSoftwareUploadBuffer() {
        long bufferSize = (long) softwareFrameWidth * (long) softwareFrameHeight * 4L;
        if (bufferSize <= 0L || bufferSize > Integer.MAX_VALUE) {
            throw new IllegalStateException("Unsupported software frame buffer size: " + bufferSize);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo bufferInfo = VkBufferCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                .size(bufferSize)
                .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

            LongBuffer pBuffer = stack.mallocLong(1);
            checkVk(vkCreateBuffer(device, bufferInfo, null, pBuffer), "vkCreateBuffer(softwareUpload)");
            softwareUploadBuffer = pBuffer.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
            vkGetBufferMemoryRequirements(device, softwareUploadBuffer, requirements);

            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(requirements.size())
                .memoryTypeIndex(findMemoryType(
                    requirements.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
                ));

            LongBuffer pMemory = stack.mallocLong(1);
            checkVk(vkAllocateMemory(device, allocInfo, null, pMemory), "vkAllocateMemory(softwareUpload)");
            softwareUploadBufferMemory = pMemory.get(0);
            checkVk(vkBindBufferMemory(device, softwareUploadBuffer, softwareUploadBufferMemory, 0L), "vkBindBufferMemory(softwareUpload)");

            PointerBuffer pMapped = stack.mallocPointer(1);
            checkVk(vkMapMemory(device, softwareUploadBufferMemory, 0L, bufferSize, 0, pMapped), "vkMapMemory(softwareUpload)");
            softwareUploadMapped = memByteBuffer(pMapped.get(0), (int) bufferSize).order(ByteOrder.nativeOrder());
            softwareUploadMappedInts = softwareUploadMapped.asIntBuffer();
        }
    }

    private void createSoftwareImage() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                .imageType(VK_IMAGE_TYPE_2D)
                .format(SOFTWARE_IMAGE_FORMAT)
                .mipLevels(1)
                .arrayLayers(1)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .tiling(VK_IMAGE_TILING_OPTIMAL)
                .usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            imageInfo.extent()
                .width(softwareFrameWidth)
                .height(softwareFrameHeight)
                .depth(1);

            LongBuffer pImage = stack.mallocLong(1);
            checkVk(vkCreateImage(device, imageInfo, null, pImage), "vkCreateImage(softwareFrame)");
            softwareImage = pImage.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
            vkGetImageMemoryRequirements(device, softwareImage, requirements);

            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                .allocationSize(requirements.size())
                .memoryTypeIndex(findMemoryType(requirements.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));

            LongBuffer pMemory = stack.mallocLong(1);
            checkVk(vkAllocateMemory(device, allocInfo, null, pMemory), "vkAllocateMemory(softwareImage)");
            softwareImageMemory = pMemory.get(0);
            checkVk(vkBindImageMemory(device, softwareImage, softwareImageMemory, 0L), "vkBindImageMemory(softwareImage)");
        }
    }

    private void createSoftwareImageView() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                .image(softwareImage)
                .viewType(VK_IMAGE_VIEW_TYPE_2D)
                .format(SOFTWARE_IMAGE_FORMAT);
            viewInfo.subresourceRange()
                .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);
            viewInfo.components()
                .r(VK_COMPONENT_SWIZZLE_IDENTITY)
                .g(VK_COMPONENT_SWIZZLE_IDENTITY)
                .b(VK_COMPONENT_SWIZZLE_IDENTITY)
                .a(VK_COMPONENT_SWIZZLE_IDENTITY);

            LongBuffer pView = stack.mallocLong(1);
            checkVk(vkCreateImageView(device, viewInfo, null, pView), "vkCreateImageView(softwareFrame)");
            softwareImageView = pView.get(0);
        }
    }

    private void createSoftwareSampler() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSamplerCreateInfo samplerInfo = VkSamplerCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                .magFilter(VK_FILTER_NEAREST)
                .minFilter(VK_FILTER_NEAREST)
                .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .anisotropyEnable(false)
                .maxAnisotropy(1.0f)
                .unnormalizedCoordinates(false)
                .compareEnable(false)
                .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                .mipLodBias(0.0f)
                .minLod(0.0f)
                .maxLod(0.0f);

            LongBuffer pSampler = stack.mallocLong(1);
            checkVk(vkCreateSampler(device, samplerInfo, null, pSampler), "vkCreateSampler(softwareFrame)");
            softwareSampler = pSampler.get(0);
        }
    }

    private void createDescriptorPoolAndSet() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
            poolSizes.get(0)
                .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .descriptorCount(1);

            VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                .maxSets(1)
                .pPoolSizes(poolSizes);

            LongBuffer pPool = stack.mallocLong(1);
            checkVk(vkCreateDescriptorPool(device, poolInfo, null, pPool), "vkCreateDescriptorPool");
            descriptorPool = pPool.get(0);

            VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                .descriptorPool(descriptorPool)
                .pSetLayouts(stack.longs(descriptorSetLayout));

            LongBuffer pSet = stack.mallocLong(1);
            checkVk(vkAllocateDescriptorSets(device, allocInfo, pSet), "vkAllocateDescriptorSets");
            descriptorSet = pSet.get(0);

            VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack);
            imageInfo.get(0)
                .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                .imageView(softwareImageView)
                .sampler(softwareSampler);

            VkWriteDescriptorSet.Buffer descriptorWrites = VkWriteDescriptorSet.calloc(1, stack);
            descriptorWrites.get(0)
                .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .dstSet(descriptorSet)
                .dstBinding(0)
                .dstArrayElement(0)
                .descriptorCount(1)
                .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                .pImageInfo(imageInfo);
            vkUpdateDescriptorSets(device, descriptorWrites, null);
        }
    }

    private void createFramebuffers() {
        swapchainFramebuffers = new long[swapchainImageViews.length];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer pFramebuffer = stack.mallocLong(1);
            for (int i = 0; i < swapchainImageViews.length; i++) {
                LongBuffer attachments = PROJECTED_WORLD_ENABLED
                    ? stack.longs(swapchainImageViews[i], depthImageView)
                    : stack.longs(swapchainImageViews[i]);
                VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO)
                    .renderPass(renderPass)
                    .pAttachments(attachments)
                    .width(swapchainWidth)
                    .height(swapchainHeight)
                    .layers(1);

                checkVk(vkCreateFramebuffer(device, framebufferInfo, null, pFramebuffer), "vkCreateFramebuffer");
                swapchainFramebuffers[i] = pFramebuffer.get(0);
            }
        }
    }

    private void allocateCommandBuffers() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferAllocateInfo allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
                .commandPool(commandPool)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(swapchainFramebuffers.length);

            PointerBuffer pCommandBuffers = stack.mallocPointer(swapchainFramebuffers.length);
            checkVk(vkAllocateCommandBuffers(device, allocInfo, pCommandBuffers), "vkAllocateCommandBuffers");

            commandBuffers = new VkCommandBuffer[swapchainFramebuffers.length];
            for (int i = 0; i < commandBuffers.length; i++) {
                commandBuffers[i] = new VkCommandBuffer(pCommandBuffers.get(i), device);
            }
        }
    }

    private void createSyncObjects() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
                .flags(VK_FENCE_CREATE_SIGNALED_BIT);

            LongBuffer pSemaphore = stack.mallocLong(1);
            LongBuffer pFence = stack.mallocLong(1);

            checkVk(vkCreateSemaphore(device, semaphoreInfo, null, pSemaphore), "vkCreateSemaphore(imageAvailable)");
            imageAvailableSemaphore = pSemaphore.get(0);
            checkVk(vkCreateSemaphore(device, semaphoreInfo, null, pSemaphore), "vkCreateSemaphore(renderFinished)");
            renderFinishedSemaphore = pSemaphore.get(0);
            checkVk(vkCreateFence(device, fenceInfo, null, pFence), "vkCreateFence");
            inFlightFence = pFence.get(0);
        }
    }

    private void renderSoftwareFrame(GameClient gameClient) {
        if (softwareFrame == null || softwareUploadMapped == null) {
            return;
        }
        Graphics2D graphics = softwareFrame.createGraphics();
        try {
            gameClient.render(graphics, softwareFrameWidth, softwareFrameHeight);
        } finally {
            graphics.dispose();
        }
        uploadSoftwareFramePixels();
    }

    private void renderProjectedUiOverlay(GameClient gameClient) {
        if (softwareFrame == null || softwareUploadMapped == null) {
            return;
        }
        Graphics2D graphics = softwareFrame.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Clear);
            graphics.fillRect(0, 0, swapchainWidth, swapchainHeight);
            graphics.setComposite(AlphaComposite.SrcOver);
            gameClient.renderUiOverlay(
                graphics,
                softwareFrameWidth,
                softwareFrameHeight,
                projectedTotalFaces,
                projectedFrustumFaceCandidates,
                projectedDrawnFaces
            );
        } finally {
            graphics.dispose();
        }
        uploadSoftwareFramePixels();
    }

    private void uploadSoftwareFramePixels() {
        if (softwareUploadMappedInts == null || softwarePixelCount <= 0) {
            return;
        }
        softwareUploadMappedInts.position(0);
        softwareUploadMappedInts.put(softwareArgbPixels, 0, softwarePixelCount);
    }

    private void updateProjectedWorldVertices(GameClient gameClient) {
        if (projectedVertexMapped == null || swapchainWidth <= 0 || swapchainHeight <= 0) {
            resetProjectedWorldFaceStats();
            return;
        }
        var player = gameClient.playerController();
        double aspect = Math.max(0.1, (double) swapchainWidth / (double) swapchainHeight);
        projectedFrustum.setCamera(
            player.eyeX(),
            player.eyeY(),
            player.eyeZ(),
            player.yaw(),
            player.pitch(),
            PROJECTED_WORLD_VERTICAL_FOV_DEGREES,
            aspect,
            PROJECTED_WORLD_NEAR_PLANE,
            PROJECTED_WORLD_FAR_PLANE
        );

        projectedVertexFloatCount = 0;
        projectedOpaqueVertexCount = 0;
        projectedTranslucentVertexCount = 0;
        projectedTranslucentFaces.clear();
        projectedTranslucentFacePoolUsed = 0;
        Mesh mesh = projectedMesher.build(gameClient.worldView(), player.y());
        projectedTotalFaces = mesh.faceCount();
        projectedFrustumFaceCandidates = 0;
        projectedDrawnFaces = 0;
        for (Mesh.ChunkBatch chunkBatch : mesh.chunks()) {
            if (!projectedFrustum.isAabbVisible(
                chunkBatch.minX(),
                chunkBatch.minY(),
                chunkBatch.minZ(),
                chunkBatch.maxX(),
                chunkBatch.maxY(),
                chunkBatch.maxZ()
            )) {
                continue;
            }
            projectedFrustumFaceCandidates += chunkBatch.faceCount();
            for (Mesh.Face face : chunkBatch.faces()) {
                appendProjectedFace(face, frameAmbient);
            }
        }

        projectedOpaqueVertexCount = projectedVertexFloatCount / PROJECTED_VERTEX_FLOATS;
        if (!projectedTranslucentFaces.isEmpty()) {
            projectedTranslucentFaces.sort(PROJECTED_TRANSLUCENT_FACE_DEPTH_DESC);
            for (ProjectedTranslucentFace translucentFace : projectedTranslucentFaces) {
                appendProjectedQuadVertices(
                    translucentFace.x0,
                    translucentFace.y0,
                    translucentFace.z0,
                    translucentFace.x1,
                    translucentFace.y1,
                    translucentFace.z1,
                    translucentFace.x2,
                    translucentFace.y2,
                    translucentFace.z2,
                    translucentFace.x3,
                    translucentFace.y3,
                    translucentFace.z3,
                    translucentFace.r,
                    translucentFace.g,
                    translucentFace.b,
                    translucentFace.a
                );
            }
        }

        projectedVertexCount = projectedVertexFloatCount / PROJECTED_VERTEX_FLOATS;
        projectedTranslucentVertexCount = Math.max(0, projectedVertexCount - projectedOpaqueVertexCount);
        if (projectedVertexCount <= 0) {
            return;
        }

        int floatsToUpload = projectedVertexFloatCount; // save before ensureCapacity may reset it
        int requiredBytes = floatsToUpload * Float.BYTES;
        ensureProjectedVertexBufferCapacityBytes(requiredBytes);
        if (projectedVertexMapped == null) {
            resetProjectedWorldFaceStats();
            return;
        }
        for (int i = 0; i < floatsToUpload; i++) {
            projectedVertexMapped.putFloat(i * Float.BYTES, projectedVertexScratch[i]);
        }
    }

    private void appendProjectedFace(Mesh.Face face, float ambient) {
        if (!projectWorldVertex(face.v0(), projectedScratchV0)
            || !projectWorldVertex(face.v1(), projectedScratchV1)
            || !projectWorldVertex(face.v2(), projectedScratchV2)
            || !projectWorldVertex(face.v3(), projectedScratchV3)) {
            return;
        }
        projectedDrawnFaces++;

        float colorScale = Math.max(0.1f, Math.min(1.0f, ambient));
        float r = (face.color().getRed() / 255.0f) * colorScale;
        float g = (face.color().getGreen() / 255.0f) * colorScale;
        float b = (face.color().getBlue() / 255.0f) * colorScale;
        float alpha = face.color().getAlpha() / 255.0f;
        if (face.renderBucket() == BlockDef.RenderBucket.TRANSLUCENT) {
            alpha = Math.min(alpha, 0.75f);
        }
        boolean needsTranslucentSort = face.renderBucket() == BlockDef.RenderBucket.TRANSLUCENT || face.needsSorting();
        if (needsTranslucentSort) {
            float sortDepth = (projectedScratchV0.cameraDepth + projectedScratchV1.cameraDepth + projectedScratchV2.cameraDepth + projectedScratchV3.cameraDepth) * 0.25f;
            ProjectedTranslucentFace translucentFace = acquireProjectedTranslucentFace();
            translucentFace.set(
                projectedScratchV0.x, projectedScratchV0.y, projectedScratchV0.z,
                projectedScratchV1.x, projectedScratchV1.y, projectedScratchV1.z,
                projectedScratchV2.x, projectedScratchV2.y, projectedScratchV2.z,
                projectedScratchV3.x, projectedScratchV3.y, projectedScratchV3.z,
                sortDepth,
                r, g, b, alpha
            );
            projectedTranslucentFaces.add(translucentFace);
            return;
        }
        appendProjectedQuadVertices(projectedScratchV0, projectedScratchV1, projectedScratchV2, projectedScratchV3, r, g, b, alpha);
    }

    private boolean projectWorldVertex(Vec3 world, ProjectedVertex out) {
        projectedFrustum.toCameraSpace(world.x(), world.y(), world.z(), projectedCameraScratch);
        double cameraZ = projectedCameraScratch.z;
        if (cameraZ <= PROJECTED_WORLD_NEAR_PLANE || cameraZ >= PROJECTED_WORLD_FAR_PLANE) {
            return false;
        }
        double tanHalfVerticalFov = Math.tan(Math.toRadians(PROJECTED_WORLD_VERTICAL_FOV_DEGREES) * 0.5);
        double tanHalfHorizontalFov = tanHalfVerticalFov * Math.max(0.1, (double) swapchainWidth / (double) swapchainHeight);
        double xNdc = projectedCameraScratch.x / (cameraZ * tanHalfHorizontalFov);
        double yNdc = projectedCameraScratch.y / (cameraZ * tanHalfVerticalFov);
        double zNdc = (cameraZ - PROJECTED_WORLD_NEAR_PLANE) / (PROJECTED_WORLD_FAR_PLANE - PROJECTED_WORLD_NEAR_PLANE);
        if (zNdc < 0.0 || zNdc > 1.0) {
            return false;
        }
        out.x = (float) xNdc;
        out.y = (float) (-yNdc);
        out.z = (float) zNdc;
        out.cameraDepth = (float) cameraZ;
        return true;
    }

    private void appendProjectedQuadVertices(
        ProjectedVertex v0,
        ProjectedVertex v1,
        ProjectedVertex v2,
        ProjectedVertex v3,
        float r,
        float g,
        float b,
        float a
    ) {
        appendProjectedVertex(v0, r, g, b, a);
        appendProjectedVertex(v1, r, g, b, a);
        appendProjectedVertex(v2, r, g, b, a);

        appendProjectedVertex(v0, r, g, b, a);
        appendProjectedVertex(v2, r, g, b, a);
        appendProjectedVertex(v3, r, g, b, a);
    }

    private void appendProjectedQuadVertices(
        float x0,
        float y0,
        float z0,
        float x1,
        float y1,
        float z1,
        float x2,
        float y2,
        float z2,
        float x3,
        float y3,
        float z3,
        float r,
        float g,
        float b,
        float a
    ) {
        appendProjectedVertex(x0, y0, z0, r, g, b, a);
        appendProjectedVertex(x1, y1, z1, r, g, b, a);
        appendProjectedVertex(x2, y2, z2, r, g, b, a);

        appendProjectedVertex(x0, y0, z0, r, g, b, a);
        appendProjectedVertex(x2, y2, z2, r, g, b, a);
        appendProjectedVertex(x3, y3, z3, r, g, b, a);
    }

    private void appendProjectedVertex(ProjectedVertex projectedVertex, float r, float g, float b, float a) {
        appendProjectedVertex(projectedVertex.x, projectedVertex.y, projectedVertex.z, r, g, b, a);
    }

    private void appendProjectedVertex(float x, float y, float z, float r, float g, float b, float a) {
        ensureProjectedScratchCapacity(PROJECTED_VERTEX_FLOATS);
        int cursor = projectedVertexFloatCount;
        projectedVertexScratch[cursor] = x;
        projectedVertexScratch[cursor + 1] = y;
        projectedVertexScratch[cursor + 2] = z;
        projectedVertexScratch[cursor + 3] = r;
        projectedVertexScratch[cursor + 4] = g;
        projectedVertexScratch[cursor + 5] = b;
        projectedVertexScratch[cursor + 6] = a;
        projectedVertexFloatCount += PROJECTED_VERTEX_FLOATS;
    }

    private ProjectedTranslucentFace acquireProjectedTranslucentFace() {
        if (projectedTranslucentFacePoolUsed >= projectedTranslucentFacePool.size()) {
            projectedTranslucentFacePool.add(new ProjectedTranslucentFace());
        }
        return projectedTranslucentFacePool.get(projectedTranslucentFacePoolUsed++);
    }

    private void resetProjectedWorldFaceStats() {
        projectedVertexFloatCount = 0;
        projectedVertexCount = 0;
        projectedOpaqueVertexCount = 0;
        projectedTranslucentVertexCount = 0;
        projectedTotalFaces = 0;
        projectedFrustumFaceCandidates = 0;
        projectedDrawnFaces = 0;
        projectedTranslucentFaces.clear();
        projectedTranslucentFacePoolUsed = 0;
    }

    private void ensureProjectedScratchCapacity(int additionalFloats) {
        int requiredFloats = projectedVertexFloatCount + additionalFloats;
        if (requiredFloats <= projectedVertexScratch.length) {
            return;
        }
        int targetLength = Math.max(requiredFloats, Math.max(projectedVertexScratch.length * 2, 4096));
        projectedVertexScratch = java.util.Arrays.copyOf(projectedVertexScratch, targetLength);
    }

    private void drawFrame() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int waitResult = vkWaitForFences(device, stack.longs(inFlightFence), true, GPU_SYNC_TIMEOUT_NANOS);
            if (waitResult == VK_TIMEOUT) {
                logGpuSyncTimeout("vkWaitForFences");
                return;
            }
            checkVk(waitResult, "vkWaitForFences");

            IntBuffer pImageIndex = stack.mallocInt(1);
            int acquireResult = vkAcquireNextImageKHR(
                device,
                swapchain,
                GPU_SYNC_TIMEOUT_NANOS,
                imageAvailableSemaphore,
                VK_NULL_HANDLE,
                pImageIndex
            );
            if (acquireResult == VK_TIMEOUT) {
                logGpuSyncTimeout("vkAcquireNextImageKHR");
                return;
            }
            if (acquireResult == VK_ERROR_OUT_OF_DATE_KHR) {
                recreateSwapchainResources();
                return;
            }
            if (acquireResult != VK_SUCCESS && acquireResult != VK_SUBOPTIMAL_KHR) {
                checkVk(acquireResult, "vkAcquireNextImageKHR");
            }

            checkVk(vkResetFences(device, stack.longs(inFlightFence)), "vkResetFences");
            int imageIndex = pImageIndex.get(0);
            recordCommandBuffer(imageIndex);

            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
                .pWaitSemaphores(stack.longs(imageAvailableSemaphore))
                .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT))
                .pCommandBuffers(stack.pointers(commandBuffers[imageIndex].address()))
                .pSignalSemaphores(stack.longs(renderFinishedSemaphore));

            checkVk(vkQueueSubmit(graphicsQueue, submitInfo, inFlightFence), "vkQueueSubmit");

            VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                .pWaitSemaphores(stack.longs(renderFinishedSemaphore))
                .swapchainCount(1)
                .pSwapchains(stack.longs(swapchain))
                .pImageIndices(stack.ints(imageIndex));

            int presentResult = vkQueuePresentKHR(presentQueue, presentInfo);
            if (framebufferResized || presentResult == VK_ERROR_OUT_OF_DATE_KHR || presentResult == VK_SUBOPTIMAL_KHR) {
                recreateSwapchainResources();
                framebufferResized = false;
                return;
            }
            if (presentResult != VK_SUCCESS) {
                checkVk(presentResult, "vkQueuePresentKHR");
            }
        }
    }

    private void logGpuSyncTimeout(String stage) {
        long now = System.nanoTime();
        if (now - lastGpuSyncTimeoutLogNanos < GPU_SYNC_TIMEOUT_LOG_THROTTLE_NANOS) {
            return;
        }
        lastGpuSyncTimeoutLogNanos = now;
        System.err.printf("[vulkan] sync timeout at %s; skipping frame to avoid hard stall.%n", stage);
    }

    private void startFrameStallWatchdog() {
        if (!FRAME_STALL_WATCHDOG_ENABLED || frameStallWatchdogRunning) {
            return;
        }
        frameStallWatchdogRunning = true;
        frameStallWatchdogThread = new Thread(() -> {
            while (frameStallWatchdogRunning) {
                try {
                    Thread.sleep(FRAME_STALL_POLL_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                long now = System.nanoTime();
                long stalledNanos = now - lastFrameProgressNanos;
                if (stalledNanos < FRAME_STALL_THRESHOLD_NANOS) {
                    continue;
                }
                if (now - lastFrameStallLogNanos < FRAME_STALL_LOG_THROTTLE_NANOS) {
                    continue;
                }
                lastFrameStallLogNanos = now;
                Thread target = renderThread;
                String stalledStage = frameStage;
                System.err.printf(
                    "[vulkan] frame stall detected stage=%s stalled=%.2fms%n",
                    stalledStage,
                    stalledNanos / 1_000_000.0
                );
                if ("poll-events".equals(stalledStage)) {
                    glfwPostEmptyEvent();
                    System.err.println("[vulkan] posted empty GLFW event to wake stalled poll-events loop.");
                    if (stalledNanos >= FRAME_STALL_ABORT_NANOS && !forceRuntimeAbort) {
                        forceRuntimeAbort = true;
                        forceRuntimeAbortReason =
                            "Vulkan event loop stalled for >10s on macOS; aborting Vulkan and falling back to software.";
                        if (windowHandle != NULL) {
                            glfwSetWindowShouldClose(windowHandle, true);
                        }
                        Thread stalledThread = renderThread;
                        if (stalledThread != null) {
                            stalledThread.interrupt();
                        }
                        System.err.println("[vulkan] forcing Vulkan runtime abort due to prolonged poll-events stall.");
                    }
                }
                if (target != null) {
                    for (StackTraceElement element : target.getStackTrace()) {
                        System.err.println("  at " + element);
                    }
                }
            }
        }, "vc-vulkan-frame-watchdog");
        frameStallWatchdogThread.setDaemon(true);
        frameStallWatchdogThread.start();
    }

    private void stopFrameStallWatchdog() {
        frameStallWatchdogRunning = false;
        Thread watchdog = frameStallWatchdogThread;
        frameStallWatchdogThread = null;
        if (watchdog != null) {
            watchdog.interrupt();
        }
    }

    private void recordCommandBuffer(int imageIndex) {
        VkCommandBuffer commandBuffer = commandBuffers[imageIndex];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            checkVk(vkResetCommandBuffer(commandBuffer, 0), "vkResetCommandBuffer");

            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
            checkVk(vkBeginCommandBuffer(commandBuffer, beginInfo), "vkBeginCommandBuffer");

            int srcStage = softwareImagePrimed ? VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT : VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
            int srcAccess = softwareImagePrimed ? VK_ACCESS_SHADER_READ_BIT : 0;
            transitionSoftwareImageLayout(
                stack,
                commandBuffer,
                softwareImagePrimed ? VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL : VK_IMAGE_LAYOUT_UNDEFINED,
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                srcStage,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                srcAccess,
                VK_ACCESS_TRANSFER_WRITE_BIT
            );
            copySoftwareFrameToImage(stack, commandBuffer);
            transitionSoftwareImageLayout(
                stack,
                commandBuffer,
                VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                VK_PIPELINE_STAGE_TRANSFER_BIT,
                VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT,
                VK_ACCESS_TRANSFER_WRITE_BIT,
                VK_ACCESS_SHADER_READ_BIT
            );
            softwareImagePrimed = true;

            float skyTopR = Math.max(0.0f, Math.min(1.0f, 0.35f * frameAmbient + 0.10f));
            float skyTopG = Math.max(0.0f, Math.min(1.0f, 0.55f * frameAmbient + 0.12f));
            float skyTopB = Math.max(0.0f, Math.min(1.0f, 0.95f * frameAmbient + 0.18f));

            VkRenderPassBeginInfo renderPassInfo = VkRenderPassBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .renderPass(renderPass)
                .framebuffer(swapchainFramebuffers[imageIndex]);
            renderPassInfo.renderArea().offset().set(0, 0);
            renderPassInfo.renderArea().extent().set(swapchainWidth, swapchainHeight);
            int clearValueCount = PROJECTED_WORLD_ENABLED ? 2 : 1;
            renderPassInfo.pClearValues(org.lwjgl.vulkan.VkClearValue.calloc(clearValueCount, stack));
            renderPassInfo.pClearValues().get(0).color()
                .float32(0, skyTopR)
                .float32(1, skyTopG)
                .float32(2, skyTopB)
                .float32(3, 1.0f);
            if (PROJECTED_WORLD_ENABLED) {
                renderPassInfo.pClearValues().get(1).depthStencil()
                    .depth(1.0f)
                    .stencil(0);
            }

            vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);
            if (PROJECTED_WORLD_ENABLED) {
                vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);
                if (projectedVertexCount > 0 && projectedVertexBuffer != VK_NULL_HANDLE) {
                    vkCmdBindVertexBuffers(commandBuffer, 0, stack.longs(projectedVertexBuffer), stack.longs(0L));
                    if (projectedOpaqueVertexCount > 0) {
                        vkCmdDraw(commandBuffer, projectedOpaqueVertexCount, 1, 0, 0);
                    }
                    if (projectedTranslucentVertexCount > 0 && projectedTranslucentPipeline != VK_NULL_HANDLE) {
                        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, projectedTranslucentPipeline);
                        vkCmdDraw(commandBuffer, projectedTranslucentVertexCount, 1, projectedOpaqueVertexCount, 0);
                    }
                }

                if (overlayPipeline != VK_NULL_HANDLE && overlayPipelineLayout != VK_NULL_HANDLE) {
                    vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, overlayPipeline);
                    vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, overlayPipelineLayout, 0, stack.longs(descriptorSet), null);
                    vkCmdDraw(commandBuffer, 3, 1, 0, 0);
                }
            } else {
                vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);
                vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineLayout, 0, stack.longs(descriptorSet), null);
                vkCmdDraw(commandBuffer, 3, 1, 0, 0);
            }
            vkCmdEndRenderPass(commandBuffer);
            checkVk(vkEndCommandBuffer(commandBuffer), "vkEndCommandBuffer");
        }
    }

    private void copySoftwareFrameToImage(MemoryStack stack, VkCommandBuffer commandBuffer) {
        VkBufferImageCopy.Buffer copyRegion = VkBufferImageCopy.calloc(1, stack);
        copyRegion.get(0)
            .bufferOffset(0L)
            .bufferRowLength(0)
            .bufferImageHeight(0);
        copyRegion.get(0).imageSubresource()
            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
            .mipLevel(0)
            .baseArrayLayer(0)
            .layerCount(1);
        copyRegion.get(0).imageOffset().set(0, 0, 0);
        copyRegion.get(0).imageExtent().set(softwareFrameWidth, softwareFrameHeight, 1);

        vkCmdCopyBufferToImage(
            commandBuffer,
            softwareUploadBuffer,
            softwareImage,
            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            copyRegion
        );
    }

    private void transitionSoftwareImageLayout(
        MemoryStack stack,
        VkCommandBuffer commandBuffer,
        int oldLayout,
        int newLayout,
        int srcStageMask,
        int dstStageMask,
        int srcAccessMask,
        int dstAccessMask
    ) {
        VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
        barrier.get(0)
            .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
            .oldLayout(oldLayout)
            .newLayout(newLayout)
            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .image(softwareImage)
            .srcAccessMask(srcAccessMask)
            .dstAccessMask(dstAccessMask);
        barrier.get(0).subresourceRange()
            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
            .baseMipLevel(0)
            .levelCount(1)
            .baseArrayLayer(0)
            .layerCount(1);

        vkCmdPipelineBarrier(
            commandBuffer,
            srcStageMask,
            dstStageMask,
            0,
            null,
            null,
            barrier
        );
    }

    private void installInputCallbacks() {
        glfwSetKeyCallback(windowHandle, (window, key, scancode, action, mods) -> {
            int mapped = mapKeyCode(key);
            if (mapped < 0) {
                return;
            }
            if (action == GLFW_PRESS) {
                input.onKeyPressed(mapped);
            } else if (action == GLFW_RELEASE) {
                input.onKeyReleased(mapped);
            }
        });

        glfwSetMouseButtonCallback(windowHandle, (window, button, action, mods) -> {
            int mapped = mapMouseButton(button);
            if (mapped < 0) {
                return;
            }
            if (action == GLFW_PRESS) {
                input.onMousePressed(mapped);
            } else if (action == GLFW_RELEASE) {
                input.onMouseReleased(mapped);
            }
        });

        glfwSetCursorPosCallback(windowHandle, (window, xpos, ypos) -> {
            int mouseX = (int) Math.round(xpos);
            int mouseY = (int) Math.round(ypos);
            if (firstMouseSample) {
                input.setMousePosition(mouseX, mouseY);
                firstMouseSample = false;
                return;
            }
            input.onMouseMoved(mouseX, mouseY);
        });

        glfwSetFramebufferSizeCallback(windowHandle, (window, width, height) -> framebufferResized = true);
    }

    private boolean hasSurfaceSupport(VkPhysicalDevice candidate) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer formatCount = stack.mallocInt(1);
            IntBuffer presentModeCount = stack.mallocInt(1);
            int fmtResult = vkGetPhysicalDeviceSurfaceFormatsKHR(candidate, surface, formatCount, null);
            int pmResult = vkGetPhysicalDeviceSurfacePresentModesKHR(candidate, surface, presentModeCount, null);
            if (fmtResult == VK_ERROR_SURFACE_LOST_KHR || pmResult == VK_ERROR_SURFACE_LOST_KHR) {
                return false;
            }
            return fmtResult == VK_SUCCESS && pmResult == VK_SUCCESS && formatCount.get(0) > 0 && presentModeCount.get(0) > 0;
        }
    }

    private boolean deviceSupportsSwapchain(VkPhysicalDevice candidate) {
        return deviceSupportsExtension(candidate, VK_KHR_SWAPCHAIN_EXTENSION_NAME);
    }

    private boolean deviceSupportsExtension(VkPhysicalDevice candidate, String extensionName) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer extensionCount = stack.mallocInt(1);
            checkVk(vkEnumerateDeviceExtensionProperties(candidate, (String) null, extensionCount, null), "vkEnumerateDeviceExtensionProperties(count)");
            if (extensionCount.get(0) <= 0) {
                return false;
            }
            VkExtensionProperties.Buffer availableExtensions = VkExtensionProperties.malloc(extensionCount.get(0), stack);
            checkVk(vkEnumerateDeviceExtensionProperties(candidate, (String) null, extensionCount, availableExtensions), "vkEnumerateDeviceExtensionProperties(list)");
            for (int i = 0; i < availableExtensions.capacity(); i++) {
                if (extensionName.equals(availableExtensions.get(i).extensionNameString())) {
                    return true;
                }
            }
            return false;
        }
    }

    private QueueFamilyIndices findQueueFamilies(VkPhysicalDevice candidate) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.mallocInt(1);
            vkGetPhysicalDeviceQueueFamilyProperties(candidate, count, null);
            VkQueueFamilyProperties.Buffer queueFamilies = VkQueueFamilyProperties.malloc(count.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(candidate, count, queueFamilies);

            int graphicsFamily = -1;
            int presentFamily = -1;
            IntBuffer presentSupport = stack.mallocInt(1);
            for (int i = 0; i < queueFamilies.capacity(); i++) {
                VkQueueFamilyProperties queueFamily = queueFamilies.get(i);
                if ((queueFamily.queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                    graphicsFamily = i;
                }
                checkVk(vkGetPhysicalDeviceSurfaceSupportKHR(candidate, i, surface, presentSupport), "vkGetPhysicalDeviceSurfaceSupportKHR");
                if (presentSupport.get(0) == VK_TRUE) {
                    presentFamily = i;
                }
                if (graphicsFamily >= 0 && presentFamily >= 0) {
                    break;
                }
            }
            return new QueueFamilyIndices(graphicsFamily, presentFamily);
        }
    }

    private SurfaceFormatChoice chooseSurfaceFormat(VkSurfaceFormatKHR.Buffer formats) {
        for (int i = 0; i < formats.capacity(); i++) {
            VkSurfaceFormatKHR surfaceFormat = formats.get(i);
            int format = surfaceFormat.format();
            int colorSpace = surfaceFormat.colorSpace();
            if ((format == VK_FORMAT_B8G8R8A8_SRGB || format == VK_FORMAT_R8G8B8A8_SRGB)
                && colorSpace == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                return new SurfaceFormatChoice(format, colorSpace);
            }
        }
        VkSurfaceFormatKHR fallback = formats.get(0);
        return new SurfaceFormatChoice(fallback.format(), fallback.colorSpace());
    }

    private static int choosePresentMode(IntBuffer presentModes) {
        if (VSYNC_ENABLED) {
            return VK_PRESENT_MODE_FIFO_KHR;
        }
        for (int i = 0; i < presentModes.capacity(); i++) {
            if (presentModes.get(i) == VK_PRESENT_MODE_MAILBOX_KHR) {
                return VK_PRESENT_MODE_MAILBOX_KHR;
            }
        }
        for (int i = 0; i < presentModes.capacity(); i++) {
            if (presentModes.get(i) == VK_PRESENT_MODE_IMMEDIATE_KHR) {
                return VK_PRESENT_MODE_IMMEDIATE_KHR;
            }
        }
        return VK_PRESENT_MODE_FIFO_KHR;
    }

    private ExtentChoice chooseSwapchainExtent(VkSurfaceCapabilitiesKHR capabilities) {
        int currentWidth = capabilities.currentExtent().width();
        int currentHeight = capabilities.currentExtent().height();
        if (currentWidth != 0xFFFFFFFF && currentHeight != 0xFFFFFFFF) {
            return new ExtentChoice(currentWidth, currentHeight);
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer widthBuffer = stack.mallocInt(1);
            IntBuffer heightBuffer = stack.mallocInt(1);
            glfwGetFramebufferSize(windowHandle, widthBuffer, heightBuffer);
            int width = clamp(widthBuffer.get(0), capabilities.minImageExtent().width(), capabilities.maxImageExtent().width());
            int height = clamp(heightBuffer.get(0), capabilities.minImageExtent().height(), capabilities.maxImageExtent().height());
            return new ExtentChoice(width, height);
        }
    }

    private boolean waitForNonZeroFramebuffer() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer widthBuffer = stack.mallocInt(1);
            IntBuffer heightBuffer = stack.mallocInt(1);
            glfwGetFramebufferSize(windowHandle, widthBuffer, heightBuffer);
            while ((widthBuffer.get(0) == 0 || heightBuffer.get(0) == 0) && !glfwWindowShouldClose(windowHandle)) {
                // Keep polling without entering native wait calls that may stall on macOS + MoltenVK.
                glfwPollEvents();
                try {
                    Thread.sleep(16L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                glfwGetFramebufferSize(windowHandle, widthBuffer, heightBuffer);
            }
            return widthBuffer.get(0) > 0 && heightBuffer.get(0) > 0;
        }
    }

    private void logSelectedDevice() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkPhysicalDeviceProperties properties = org.lwjgl.vulkan.VkPhysicalDeviceProperties.malloc(stack);
            vkGetPhysicalDeviceProperties(physicalDevice, properties);
            System.out.printf(
                "[vulkan] device=%s graphicsQueueFamily=%d presentQueueFamily=%d swapchainImages=%d extent=%dx%d vsync=%s%n",
                properties.deviceNameString(),
                graphicsQueueFamily,
                presentQueueFamily,
                swapchainImages.length,
                swapchainWidth,
                swapchainHeight,
                VSYNC_ENABLED ? "on" : "off"
            );
        }
    }

    private int findMemoryType(int typeFilter, int requiredProperties) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceMemoryProperties memoryProperties = VkPhysicalDeviceMemoryProperties.malloc(stack);
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties);
            for (int i = 0; i < memoryProperties.memoryTypeCount(); i++) {
                int typeMatches = typeFilter & (1 << i);
                int propertyFlags = memoryProperties.memoryTypes(i).propertyFlags();
                if (typeMatches != 0 && (propertyFlags & requiredProperties) == requiredProperties) {
                    return i;
                }
            }
        }
        throw new IllegalStateException("Failed to find Vulkan memory type for properties=" + requiredProperties);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean booleanPropertyCompat(String key, String legacyKey, boolean defaultValue) {
        String raw = System.getProperty(key);
        if (raw == null) {
            raw = System.getProperty(legacyKey);
        }
        if (raw == null) {
            return defaultValue;
        }
        String normalized = raw.trim().toLowerCase();
        if (normalized.equals("1") || normalized.equals("true") || normalized.equals("yes") || normalized.equals("on")) {
            return true;
        }
        if (normalized.equals("0") || normalized.equals("false") || normalized.equals("no") || normalized.equals("off")) {
            return false;
        }
        return defaultValue;
    }

    private static int intPropertyCompat(String key, String legacyKey, int defaultValue) {
        String raw = System.getProperty(key);
        if (raw == null) {
            raw = System.getProperty(legacyKey);
        }
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static void checkVk(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " failed with Vulkan error code " + result);
        }
    }

    private static int mapMouseButton(int glfwButton) {
        return switch (glfwButton) {
            case GLFW_MOUSE_BUTTON_LEFT -> MouseEvent.BUTTON1;
            case GLFW_MOUSE_BUTTON_RIGHT -> MouseEvent.BUTTON3;
            default -> -1;
        };
    }

    private static int mapKeyCode(int glfwKey) {
        return switch (glfwKey) {
            case GLFW_KEY_W -> KeyEvent.VK_W;
            case GLFW_KEY_S -> KeyEvent.VK_S;
            case GLFW_KEY_A -> KeyEvent.VK_A;
            case GLFW_KEY_D -> KeyEvent.VK_D;
            case GLFW_KEY_E -> KeyEvent.VK_E;
            case GLFW_KEY_O -> KeyEvent.VK_O;
            case GLFW_KEY_Q -> KeyEvent.VK_Q;
            case GLFW_KEY_R -> KeyEvent.VK_R;
            case GLFW_KEY_V -> KeyEvent.VK_V;
            case GLFW_KEY_X -> KeyEvent.VK_X;
            case GLFW_KEY_Z -> KeyEvent.VK_Z;
            case GLFW_KEY_SPACE -> KeyEvent.VK_SPACE;
            case GLFW_KEY_LEFT_SHIFT -> KeyEvent.VK_SHIFT;
            case GLFW_KEY_LEFT -> KeyEvent.VK_LEFT;
            case GLFW_KEY_RIGHT -> KeyEvent.VK_RIGHT;
            case GLFW_KEY_UP -> KeyEvent.VK_UP;
            case GLFW_KEY_DOWN -> KeyEvent.VK_DOWN;
            case GLFW_KEY_ESCAPE -> KeyEvent.VK_ESCAPE;
            case GLFW_KEY_1 -> KeyEvent.VK_1;
            case GLFW_KEY_2 -> KeyEvent.VK_2;
            case GLFW_KEY_3 -> KeyEvent.VK_3;
            case GLFW_KEY_4 -> KeyEvent.VK_4;
            case GLFW_KEY_5 -> KeyEvent.VK_5;
            case GLFW_KEY_6 -> KeyEvent.VK_6;
            case GLFW_KEY_7 -> KeyEvent.VK_7;
            case GLFW_KEY_KP_1 -> KeyEvent.VK_NUMPAD1;
            case GLFW_KEY_KP_2 -> KeyEvent.VK_NUMPAD2;
            case GLFW_KEY_KP_3 -> KeyEvent.VK_NUMPAD3;
            case GLFW_KEY_KP_4 -> KeyEvent.VK_NUMPAD4;
            case GLFW_KEY_KP_5 -> KeyEvent.VK_NUMPAD5;
            case GLFW_KEY_KP_6 -> KeyEvent.VK_NUMPAD6;
            case GLFW_KEY_KP_7 -> KeyEvent.VK_NUMPAD7;
            default -> -1;
        };
    }

    private static final class ProjectedVertex {
        private float x;
        private float y;
        private float z;
        private float cameraDepth;
    }

    private static final class ProjectedTranslucentFace {
        private float x0;
        private float y0;
        private float z0;
        private float x1;
        private float y1;
        private float z1;
        private float x2;
        private float y2;
        private float z2;
        private float x3;
        private float y3;
        private float z3;
        private float sortDepth;
        private float r;
        private float g;
        private float b;
        private float a;

        private void set(
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float x3,
            float y3,
            float z3,
            float sortDepth,
            float r,
            float g,
            float b,
            float a
        ) {
            this.x0 = x0;
            this.y0 = y0;
            this.z0 = z0;
            this.x1 = x1;
            this.y1 = y1;
            this.z1 = z1;
            this.x2 = x2;
            this.y2 = y2;
            this.z2 = z2;
            this.x3 = x3;
            this.y3 = y3;
            this.z3 = z3;
            this.sortDepth = sortDepth;
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
        }

        private float sortDepth() {
            return sortDepth;
        }
    }

    private static final class QueueFamilyIndices {
        private final int graphicsFamily;
        private final int presentFamily;

        private QueueFamilyIndices(int graphicsFamily, int presentFamily) {
            this.graphicsFamily = graphicsFamily;
            this.presentFamily = presentFamily;
        }

        private boolean complete() {
            return graphicsFamily >= 0 && presentFamily >= 0;
        }
    }

    private static final class SurfaceFormatChoice {
        private final int format;
        private final int colorSpace;

        private SurfaceFormatChoice(int format, int colorSpace) {
            this.format = format;
            this.colorSpace = colorSpace;
        }
    }

    private static final class ExtentChoice {
        private final int width;
        private final int height;

        private ExtentChoice(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }
}
